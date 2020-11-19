package com.fc.springcloud.provider.Impl.hcloudprovider;

import com.fc.springcloud.provider.Impl.hcloudprovider.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.WorkerNotFoundException;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jointfaas.manager.ManagerGrpc.ManagerImplBase;
import jointfaas.manager.ManagerOuterClass.RegisterResponse;
import lombok.Getter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

@Getter
public class WorkerMaintainerServer extends ManagerImplBase {

  private static final Log logger = LogFactory.getLog(WorkerMaintainerServer.class);
  // todo find a storage to store these data;
  private Map<String, Worker> workers;
  private Map<String, List<String>> functionWorkerMap;
  private ReadWriteLock lock;
  private Server server;
  private int port;

  public WorkerMaintainerServer(int port) {
    workers = new HashMap<>();
    functionWorkerMap = new HashMap<>();
    lock = new ReentrantReadWriteLock();
    this.port = port;
  }

  public void start() throws IOException {
    logger.info(port);
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
  public void register(jointfaas.manager.ManagerOuterClass.RegisterRequest request,
      io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.RegisterResponse> responseObserver) {
    Lock writeLock = lock.writeLock();
    Worker worker = new Worker();
    worker.setIdentity(request.getId());
    worker.setAddr(request.getAddr());
    worker.setChannel(ManagedChannelBuilder.forTarget(request.getAddr()).usePlaintext().build());
    writeLock.lock();
    if (workers.get(request.getId()) != null) {
      // worker re register
      responseObserver.onNext(RegisterResponse.newBuilder()
          .setMsg("you has already registered")
          .setCode(RegisterResponse.Code.ERROR)
          .build());
    } else {
      workers.put(request.getId(), worker);
      responseObserver.onNext(RegisterResponse.newBuilder()
          .setMsg("register success")
          .setCode(RegisterResponse.Code.OK)
          .build());
      logger.info("register success");
    }
    responseObserver.onCompleted();
    writeLock.unlock();
  }

  public byte[] invokeFunction(Resource resource, byte[] input) {
    List<String> workerList = functionWorkerMap.get(resource.funcName);
    Worker worker = null;
    if (workerList == null) {
      // workers which can run the function are not found.
      // here you should initFunction for a new worker.

      // find a worker randomly
       List<String> workerIds = new ArrayList<String>(workers.keySet());
       worker = workers.get(workerIds.get((int) (Math.random() * workerIds.size()) ));
       initFunction(worker.getIdentity(), resource);
    }  else {
      // randomly choose a worker in the array.
      worker = workers.get(workerList.get( (int) (Math.random() * workerList.size())));
    }
    byte[] output = null;
    try {
      output = worker.invoke(resource.funcName, input);
    } catch (ChannelException e) {
      logger.fatal(e.getMessage() + " " + e.getIdentity());
      throw e;
    } catch (InvokeException e) {
      // todo here can handle retry, now all will throw exception;
      logger.fatal(e.getMessage());
      throw e;
    }
    return output;
  }

  // initFunction will be call before invokeFunction
  public void initFunction(String workerId, Resource resource) {
    Worker worker = workers.get(workerId);
    if (worker == null) {
      throw new WorkerNotFoundException("worker not found in initFunction", workerId);
    }
    try {
      worker.initFunction(resource.funcName, resource.image, resource.runtime, resource.codeURI,
          resource.memorySize, resource.timeout);
    } catch (InitFunctionException | ChannelException e) {
      logger.error(e);
      // todo handle exception
      return;
    }
    // write into the functionWorkerMap
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    List<String> workerList = functionWorkerMap.get(resource.funcName);
    if (workerList == null) {
      workerList = new ArrayList<>();
    }
    workerList.add(workerId);
    functionWorkerMap.put(resource.funcName, workerList);
    writeLock.unlock();
  }

  public void DeleteFunction(String funcName) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    functionWorkerMap.remove(funcName);
    writeLock.unlock();
  }

  public boolean hasWorker(){
    logger.info(workers);
    return workers.size() != 0 ;
  }
}
