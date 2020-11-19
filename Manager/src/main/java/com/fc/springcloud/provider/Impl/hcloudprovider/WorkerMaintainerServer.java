package com.fc.springcloud.provider.Impl.hcloudprovider;

import com.fc.springcloud.provider.Impl.hcloudprovider.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.WorkerNotFoundException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jointfaas.manager.ManagerGrpc.ManagerImplBase;
import jointfaas.manager.ManagerOuterClass.RegisterResponse;
import jointfaas.worker.HeartBeatRequest;
import jointfaas.worker.HeartBeatResponse;
import jointfaas.worker.WorkerGrpc;
import jointfaas.worker.WorkerGrpc.WorkerStub;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
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

  private ExecutorService heartbeats;

  public WorkerMaintainerServer(int port) {
    workers = new HashMap<>();
    functionWorkerMap = new HashMap<>();
    lock = new ReentrantReadWriteLock();
    this.port = port;
    heartbeats = Executors.newCachedThreadPool();
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
  @Setter
  static class HeartBeatClient implements StreamObserver<HeartBeatResponse> {

    private Condition condition;
    private Lock conditionLock;
    private ReadWriteLock lock;
    private Map<String, Worker> workers;
    private String workerId;
    private Boolean stop;
    private StreamObserver<HeartBeatRequest> heartBeatRequestObserver;
    public HeartBeatClient(ReadWriteLock lock, Map<String, Worker> workers, String workerId) {
      this.lock = lock;
      this.conditionLock = new ReentrantLock();
      this.condition = conditionLock.newCondition();
      this.workers = workers;
      this.workerId = workerId;
      this.stop = false;
    }

    @SneakyThrows
    public void start() {
      do {
        this.conditionLock.lock();
        heartBeatRequestObserver.onNext(HeartBeatRequest.newBuilder().setNonce(workerId).build());
        Thread.sleep(5000);
        try {
          condition.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } while(!stop);
    }

    @Override
    public void onNext(HeartBeatResponse heartBeatResponse) {
      logger.info("heartbeat onNext");
      logger.info(heartBeatResponse.getNonce());
      conditionLock.lock();
      condition.signalAll();
      conditionLock.unlock();
    }

    @Override
    public void onError(Throwable throwable) {
      logger.info("heartbeat onError");
      logger.fatal(throwable);
      this.onCompleted();
      this.stop = true;
    }

    @Override
    public void onCompleted() {
      logger.info("completed");
      Lock workerLock = lock.writeLock();
      workerLock.lock();
      workers.remove(workerId);
      workerLock.unlock();
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
      ManagedChannel beatChannel = ManagedChannelBuilder.forTarget(request.getAddr()).usePlaintext()
          .build();
      worker.setHeartbeatChannel(beatChannel);
      heartbeats.execute(new Runnable() {
        @Override
        public void run() {
          String workerId = new String(worker.getIdentity());
          WorkerStub heartBeatClient = WorkerGrpc.newStub(beatChannel);
          HeartBeatClient hbc = new HeartBeatClient(lock, workers, workerId);
          StreamObserver<HeartBeatRequest> heartBeatRequestObserver = heartBeatClient.getHeartBeat(hbc);
          hbc.setHeartBeatRequestObserver(heartBeatRequestObserver);
          hbc.start();
        }
      });
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
