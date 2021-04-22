package com.fc.springcloud.docker;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;
import com.google.protobuf.ByteString;
import com.fc.springcloud.dto.Resource;
import com.fc.springcloud.exception.ContainerNotFoundException;
import com.fc.springcloud.exception.LoadCodeException;
import com.fc.springcloud.exception.NoSuchFunctionException;
import com.fc.springcloud.policy.PolicyBuilder;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jointfaas.manager.ManagerGrpc;
import jointfaas.manager.ManagerGrpc.ManagerBlockingStub;
import jointfaas.manager.ManagerOuterClass;
import jointfaas.manager.ManagerOuterClass.RegisterRequest;
import jointfaas.manager.ManagerOuterClass.RegisterResponse.Code;
import jointfaas.worker.CreateContainerResp;
import jointfaas.worker.DeleteContainerResp;
import jointfaas.worker.HeartBeatRequest;
import jointfaas.worker.HeartBeatResponse;
import jointfaas.worker.InitFunctionResponse;
import jointfaas.worker.InvokeResponse;
import jointfaas.worker.RegisterResponse;
import jointfaas.worker.WorkerGrpc.WorkerImplBase;

public class WorkerServer extends WorkerImplBase {

  private static final Logger logger = Logger.getLogger(WorkerServer.class.getName());

  private final ContainerMaintainer maintainer;

  private final Properties config;

  private Server server;

  public WorkerServer(ContainerMaintainer maintainer, Properties prop) {
    this.maintainer = maintainer;
    this.config = prop;
  }

  private class RegisterHandler implements Callable<Boolean> {

    ManagedChannel channel;

    public RegisterHandler(ManagedChannel channel) {
      this.channel = channel;
    }

    @Override
    public Boolean call() throws Exception {
      try {
        ManagerBlockingStub registerClient = ManagerGrpc.newBlockingStub(channel);
        ManagerOuterClass.RegisterResponse response = registerClient.register(
            RegisterRequest.newBuilder()
                .setAddr(config.getProperty("addr", "127.0.0.1:8001"))
                .setId(config.getProperty("id", "1"))
                .build()
        );
        if (response.getCode() == Code.ERROR) {
          logger.log(Level.OFF, response.getMsg());
          return false;
        }
        return true;
      } catch (StatusRuntimeException e) {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        logger.info(e.getMessage());
        logger.warning("register failed retry after seconds");
        return false;
      }
    }
  }

  // Start will deal with worker itself register to Manager.
  public void Start(ManagedChannel channel) throws IOException, InterruptedException {
    try {
      this.maintainer.InitWorkingContainers();
    } catch (Exception e) {
      logger.warning(e.getLocalizedMessage());
    }
    // todo see local docker container first to init ContainerMaintainer workingContainers
    int port = Integer.parseInt(config.getProperty("port", "8001"));
    server = ServerBuilder.forPort(port)
        .addService(this)
        .build()
        .start();
    logger.info("Server started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        System.err.println("*** server shut down");
      }
    });
    logger.log(Level.INFO, this.config.getProperty("manager"));

    Callable<Boolean> callable = new RegisterHandler(channel);

    Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
        .retryIfResult(Predicates.<Boolean>alwaysFalse())
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.exponentialWait(100, 1, TimeUnit.SECONDS))
        .withStopStrategy(StopStrategies.neverStop())
        .build();
    try {
      retryer.call(callable);
    } catch (RetryException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  // choose a container to run function with parameters, or if no containers can run function, create function and return fast.
  @Override
  public void invoke(jointfaas.worker.InvokeRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.InvokeResponse> responseObserver) {
    String funcName = request.getName();
    ByteString payload = request.getPayload();
    ByteString output = null;
    try {
      output = this.maintainer
          .Invoke(funcName, payload, PolicyBuilder.Build(config.getProperty("policy", "simple")));
      logger.info("invoke " + request.getName());
      if (output != null) {
        logger.info("invoke resp" + new String(output.toString()));
      }
    } catch (NoSuchFunctionException e) {
      logger.info(e.getMessage() + " " + e.getFunctionName());
      InvokeResponse res = InvokeResponse.newBuilder()
          .setCode(InvokeResponse.Code.NO_SUCH_FUNCTION)
          .build();
      responseObserver.onNext(res);
      responseObserver.onCompleted();
      return;
    }
    InvokeResponse res = null;
    if (output == null) {
      res = InvokeResponse.newBuilder()
          .setCode(InvokeResponse.Code.RETRY)
          .build();
    } else {
      res = InvokeResponse.newBuilder()
          .setCode(InvokeResponse.Code.OK)
          .setOutput(output).build();
    }
    responseObserver.onNext(res);
    responseObserver.onCompleted();
  }

  @Override
  public io.grpc.stub.StreamObserver<jointfaas.worker.HeartBeatRequest> getHeartBeat(
      final io.grpc.stub.StreamObserver<jointfaas.worker.HeartBeatResponse> responseObserver) {
    return new io.grpc.stub.StreamObserver<jointfaas.worker.HeartBeatRequest>() {
      private Boolean init = false;

      @Override
      public void onNext(HeartBeatRequest heartBeatRequest) {
        // todo check nonce == workerid;
        if (!init) {
          maintainer.startSync();
        }
        init = true;

        responseObserver
            .onNext(HeartBeatResponse.newBuilder().setNonce(heartBeatRequest.getNonce()).build());
      }

      @Override
      public void onError(Throwable throwable) {
        logger.warning(throwable.getMessage());
        logger.warning("worker disconnect with manager");
      }

      @Override
      public void onCompleted() {
        responseObserver.onCompleted();
      }
    };
  }


  // handle container register request, store Container to memory
  // todo allow working container re-register
  @Override
  public void register(jointfaas.worker.RegisterRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.RegisterResponse> responseObserver) {
    logger.info(request.getId() + " is register with ip " + request.getAddr());
    RegisterResponse res = null;
    try {
      this.maintainer.RegisterHandle(request.getId(), request.getAddr(), request.getFuncName(),
          request.getRuntime(), request.getMemory(), request.getDisk());
      res = RegisterResponse.newBuilder()
          .setCode(RegisterResponse.Code.OK)
          .build();
    } catch (LoadCodeException e) {
      res = RegisterResponse.newBuilder()
          .setCode(RegisterResponse.Code.ERROR)
          .setMsg("container " + e.getContainerId() + "load code error")
          .build();
    } catch (ContainerNotFoundException e) {
      res = RegisterResponse.newBuilder()
          .setCode(RegisterResponse.Code.ERROR)
          .setMsg("container " + e.getContainerId() + "is not existed")
          .build();
    }
    responseObserver.onNext(res);
    responseObserver.onCompleted();
  }

  @Override
  public void reset(jointfaas.worker.ResetRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.ResetResponse> responseObserver) {
    // todo move working runtime to idles runtime
    responseObserver.onCompleted();
  }

  // initFunction's caller is Manager, which will give function information.
  @Override
  public void initFunction(jointfaas.worker.InitFunctionRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.InitFunctionResponse> responseObserver) {
    // create dto here
    Resource resource = new Resource(request);
    this.maintainer.InitFunction(resource);
    responseObserver.onNext(InitFunctionResponse.newBuilder()
        .setCode(InitFunctionResponse.Code.OK)
        .setMsg("success")
        .build());
    logger.info("init function over:" + request.getFuncName() + " " + request.getCodeURI());
    responseObserver.onCompleted();
  }

  @Override
  public void metrics(jointfaas.worker.MetricsRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.MetricsResponse> responseObserver) {
    // todo maybe will deprecated
    responseObserver.onCompleted();
  }

  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  public void stop() throws InterruptedException {
    if (server != null) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Override
  public void createContainer(jointfaas.worker.CreateContainerRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.CreateContainerResp> responseObserver) {
    try {
      this.maintainer.CreateContainer(request.getFuncName(), 1);
    } catch (NoSuchFunctionException e) {
      responseObserver.onNext(CreateContainerResp.newBuilder()
          .setCode(CreateContainerResp.Code.NO_SUCH_FUNCTION)
          .build());
      responseObserver.onCompleted();
      return;
    }
    responseObserver.onNext(CreateContainerResp.newBuilder()
        .setCode(CreateContainerResp.Code.OK)
        .build());
    responseObserver.onCompleted();
  }

  @Override
  public void deleteContainer(jointfaas.worker.DeleteContainerRequest request,
      io.grpc.stub.StreamObserver<jointfaas.worker.DeleteContainerResp> responseObserver) {
    if (config.getProperty("deletePolicy", "delete").equals("delete")) {
      try {
        this.maintainer.DeleteContainer(request.getFuncName(), request.getNum());
      } catch (NoSuchFunctionException e) {
        responseObserver.onNext(DeleteContainerResp.newBuilder()
            .setCode(DeleteContainerResp.Code.NO_SUCH_FUNCTION)
            .build());
        responseObserver.onCompleted();
        return;
      }
    } else {
      // pause/unpause first
      try {
        this.maintainer.PauseContainer(request.getFuncName(), request.getNum());
      } catch (NoSuchFunctionException e) {
        responseObserver.onNext(DeleteContainerResp.newBuilder()
            .setCode(DeleteContainerResp.Code.NO_SUCH_FUNCTION)
            .build());
        responseObserver.onCompleted();
        return;
      }
    }
    responseObserver.onNext(DeleteContainerResp.newBuilder()
        .setCode(DeleteContainerResp.Code.OK).build());
    responseObserver.onCompleted();
  }
}
