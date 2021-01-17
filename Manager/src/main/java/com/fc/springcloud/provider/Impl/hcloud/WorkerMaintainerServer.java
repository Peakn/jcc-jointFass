package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.pojo.dto.GatewayEvent;
import com.fc.springcloud.provider.Impl.hcloud.Worker.Status;
import com.fc.springcloud.provider.Impl.hcloud.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloud.exception.NoWorkerException;
import com.fc.springcloud.provider.Impl.hcloud.exception.WorkerNotFoundException;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jointfaas.manager.ManagerGrpc.ManagerImplBase;
import jointfaas.manager.ManagerOuterClass;
import jointfaas.manager.ManagerOuterClass.CodeStartResponse;
import jointfaas.manager.ManagerOuterClass.RegisterResponse;
import jointfaas.manager.ManagerOuterClass.SyncRequest;
import jointfaas.manager.ManagerOuterClass.SyncResponse;
import jointfaas.manager.ManagerOuterClass.SyncResponse.Code;
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
  private Map<String, Boolean> hasChanged;
  private BlockingQueue<GatewayEvent> gatewayEvents;
  private Lock hasChangedLock;
  private ReadWriteLock lock;
  private Server server;
  private int port;

  private ExecutorService heartbeats;

  public WorkerMaintainerServer(int port, Map<String, Boolean> hasChanged, Lock hasChangedLock,
      BlockingQueue<GatewayEvent> gatewayEvents) {
    this.workers = new HashMap<>();
    this.functionWorkerMap = new HashMap<>();
    this.lock = new ReentrantReadWriteLock();
    this.port = port;
    this.heartbeats = Executors.newCachedThreadPool();
    this.hasChanged = hasChanged;
    this.hasChangedLock = hasChangedLock;
    this.gatewayEvents = gatewayEvents;
  }

  public List<String> GetInstanceByFunctionName(String functionName) {
    Lock readLock = this.lock.readLock();
    readLock.lock();
    List<String> total = new ArrayList<>();
    for (String id : this.workers.keySet()) {
      Worker worker = this.workers.get(id);
      Map<String, List<String>> instanceByFunction = worker.getInstances();
      List<String> instances = instanceByFunction.get(functionName);
      if (instances != null) {
        total.addAll(instances);
      }
    }
    readLock.unlock();
    return total;
  }

  public void Start() throws IOException {
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

  // per worker per connection
  @Override
  public io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.SyncRequest> sync(
      io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.SyncResponse> responseObserver) {
    return new io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.SyncRequest>() {


      private String workerId = "";

      @Override
      public void onNext(SyncRequest syncRequest) {
        logger.info("in the sync get request:" + syncRequest.toString());
        workerId = syncRequest.getWorkerId();
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        Worker worker = workers.get(workerId);
        Map<String, List<String>> instanceByFunction = worker.getInstances();
        instanceByFunction.put(syncRequest.getFunctionName(), syncRequest.getInstancesList());
        writeLock.unlock();
        hasChangedLock.lock();
        logger.info("put " + syncRequest.getFunctionName() + " has changed true");
        hasChanged.put(syncRequest.getFunctionName(), true);
        hasChangedLock.unlock();
        responseObserver.onNext(SyncResponse.newBuilder()
            .setCode(Code.OK)
            .build());
      }

      @Override
      public void onError(Throwable throwable) {
        // handle error about clean up all instance in the worker
        logger.error(throwable.getMessage());
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        Worker worker = workers.get(workerId);
        hasChangedLock.lock();
        if (worker != null) {
          for (String key : worker.getInstances().keySet()) {
            hasChanged.put(key, true);
          }
          worker.setInstances(new HashMap<>());
          hasChangedLock.unlock();
          // the node is unhealthy, need clean up all containers' information on it
          logger.error("worker:" + workerId + "is unhealthy, clean up all containers");
        }
        writeLock.unlock();
      }


      @Override
      public void onCompleted() {

      }
    };
  }

  @Setter
  static class HeartBeatClient implements StreamObserver<HeartBeatResponse> {

    private Condition condition;
    private Lock conditionLock;
    private ReadWriteLock lock;
    private Map<String, Worker> workers;
    private String workerId;
    private Map<String, Boolean> hasChanged;
    private Lock hasChangedLock;
    private Boolean stop;
    private StreamObserver<HeartBeatRequest> heartBeatRequestObserver;

    public HeartBeatClient(ReadWriteLock lock, Map<String, Worker> workers, Lock hasChangedLock,
        Map<String, Boolean> hasChanged, String workerId) {
      this.lock = lock;
      this.conditionLock = new ReentrantLock();
      this.condition = conditionLock.newCondition();
      this.workers = workers;
      this.workerId = workerId;
      this.hasChangedLock = hasChangedLock;
      this.hasChanged = hasChanged;
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
      } while (!stop);
    }

    @Override
    public void onNext(HeartBeatResponse heartBeatResponse) {
      conditionLock.lock();
      condition.signalAll();
      conditionLock.unlock();
    }

    @Override
    public void onError(Throwable throwable) {
      logger.info(workerId + " heartbeat onError");
      logger.info(throwable);
      this.onCompleted();
      this.stop = true;
    }

    @Override
    public void onCompleted() {
      // change the state of the worker
      Lock workerLock = lock.writeLock();
      workerLock.lock();
      Worker worker = workers.get(workerId);
      worker.setStatus(Status.UNHEALTHY);
      this.hasChangedLock.lock();
      for (String function : worker.getInstances().keySet()) {
        this.hasChanged.put(function, true);
      }
      worker.setInstances(new HashMap<>());
      this.hasChangedLock.unlock();
      workerLock.unlock();
      logger.warn("worker " + workerId + " has completed");
    }
  }

  @Override
  public void coldStart(jointfaas.manager.ManagerOuterClass.CodeStartRequest request,
      io.grpc.stub.StreamObserver<ManagerOuterClass.CodeStartResponse> responseObserver) {
    GatewayEvent event = new GatewayEvent(request.getFunctionName(), request.getApplicationName());
    logger.info("get event from gateway");
    logger.info(request);
    this.gatewayEvents.add(event);
    responseObserver
        .onNext(CodeStartResponse.newBuilder().setCode(CodeStartResponse.Code.OK).build());
    responseObserver.onCompleted();
  }

  @Override
  public void register(jointfaas.manager.ManagerOuterClass.RegisterRequest request,
      io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.RegisterResponse> responseObserver) {
    Lock writeLock = lock.writeLock();
    Worker worker = new Worker();
    worker.setIdentity(request.getId());
    worker.setAddr(request.getAddr());
    worker.setChannel(ManagedChannelBuilder.forTarget(request.getAddr()).usePlaintext().build());
    worker.setInstances(new HashMap<>());
    writeLock.lock();
    if (workers.get(request.getId()) != null) {
      // worker re register
      workers.get(request.getId()).setStatus(Status.RUNNING);
      responseObserver.onNext(RegisterResponse.newBuilder()
          .setMsg("you has already registered")
          .setCode(RegisterResponse.Code.ERROR)
          .build());
    } else {
      workers.put(request.getId(), worker);
      logger.info("request worker id:" + request.getId());
      heartbeats.execute(new Runnable() {
        @Override
        public void run() {
          ManagedChannel beatChannel = ManagedChannelBuilder.forTarget(request.getAddr())
              .usePlaintext()
              .build();
          worker.setHeartbeatChannel(beatChannel);
          String workerId = new String(request.getId());
          WorkerStub heartBeatClient = WorkerGrpc.newStub(beatChannel);
          HeartBeatClient hbc = new HeartBeatClient(lock, workers, hasChangedLock, hasChanged,
              workerId);
          StreamObserver<HeartBeatRequest> heartBeatRequestObserver = heartBeatClient
              .getHeartBeat(hbc);
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
      worker = workers.get(workerIds.get((int) (Math.random() * workerIds.size())));
      InitFunction(worker.getIdentity(), resource);
    } else {
      // randomly choose a worker in the array.
      worker = workers.get(workerList.get((int) (Math.random() * workerList.size())));
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
  public void InitFunction(String workerId, Resource resource) {
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

  public boolean hasWorker() {
    if (workers.isEmpty()) {
      return false;
    }
    for (Worker worker : workers.values()
    ) {
      if (worker.getStatus().equals(Status.RUNNING)) {
        return true;
      }
    }
    return false;
  }

  private Worker chooseWorker(Resource resource) {
    Lock readLock = lock.readLock();
    readLock.lock();
    Worker target = null;
    Integer lowerBound = 99999;
    for (String workerId : this.workers.keySet()) {
      Worker worker = this.workers.get(workerId);
      if (worker.getStatus().equals(Status.UNHEALTHY)) {
        continue;
      }
      Integer total = worker.GetTotalInstances();
      if (total < lowerBound) {
        target = worker;
      }
    }
    readLock.unlock();
    return target;
  }

  public void CreateInstance(Resource resource, Integer targetNum) {
    if (!hasWorker()) {
      logger.warn("there are no workers");
      throw new NoWorkerException("there are no workers");
    }
    int increaseNum = targetNum - this.GetInstanceByFunctionName(resource.funcName).size();
    logger.info("increase function: " + resource.funcName + " with result " + increaseNum);
    // choose the first one now, here we can add policy to choose
    for (int i = 0; i < increaseNum; ++i) {
      Worker target = chooseWorker(resource);
      if (target != null && target.getStatus().equals(Status.RUNNING)) {
        target.initFunction(resource.funcName, resource.image, resource.runtime, resource.codeURI,
            resource.memorySize, resource.timeout);
        target.CreateContainer(resource);
      }
    }
  }
}
