package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.pojo.dto.GatewayEvent;
import com.fc.springcloud.provider.Impl.hcloud.Worker.Status;
import com.fc.springcloud.provider.Impl.hcloud.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloud.exception.CreateContainerException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloud.exception.NoRuntimeException;
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
import java.util.concurrent.ConcurrentHashMap;
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

  public Long getInstancesNumByWithPreFunctionName(String functionName) {
    Long target = 0L;
    for (Worker worker : this.workers.values()) {
      target += worker.GetInstancesNumByFunctionName(functionName);
      target += worker.GetPreInstancesByFunctionName(functionName);
    }
    return target;
  }

  private List<String> getInstanceByFunctionName(String functionName) {
    List<String> total = new ArrayList<>();
    for (String id : this.workers.keySet()) {
      Worker worker = this.workers.get(id);
      List<String> instances = worker.GetInstancesByFunctionName(functionName);
      if (instances != null) {
        total.addAll(instances);
      }
    }
    return total;
  }

  public List<String> GetInstanceByFunctionName(String functionName) {
    Lock readLock = this.lock.readLock();
    readLock.lock();
    logger.debug("lock at 99");
    List<String> total = getInstanceByFunctionName(functionName);
    readLock.unlock();
    logger.debug("unlock at 104");
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
        logger.debug("in the sync 0");
        workerId = syncRequest.getWorkerId();
        Lock writeLock = lock.writeLock();
        logger.debug("in the sync 1");
        writeLock.lock();
        logger.debug("lock at 151");
        logger.debug("in the sync 2");
        Worker worker = workers.get(workerId);
        logger.debug("in the sync 3");
        worker.SyncInstances(syncRequest.getFunctionName(), syncRequest.getInstancesList());
        logger.debug("in the sync 4");
        writeLock.unlock();
        logger.debug("unlock at 160");
        logger.debug("in the sync 5");
        hasChangedLock.lock();
        logger.debug("in the sync 6");
        logger.info("put " + syncRequest.getFunctionName() + " has changed true");
        logger.debug("in the sync 7");
        hasChanged.put(syncRequest.getFunctionName(), true);
        logger.debug("in the sync 8");
        hasChangedLock.unlock();
        logger.debug("in the sync 9");
        responseObserver.onNext(SyncResponse.newBuilder()
            .setCode(Code.OK)
            .build());
        logger.info("in the sync 10");
      }

      @Override
      public void onError(Throwable throwable) {
        // handle error about clean up all instance in the worker
        logger.error(throwable.getMessage());
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        logger.debug("lock at 181");
        Worker worker = workers.get(workerId);
        hasChangedLock.lock();
        if (worker != null) {
          for (String key : worker.getInstances().keySet()) {
            hasChanged.put(key, true);
          }
          worker.setInstances(new HashMap<>());
          // the node is unhealthy, need clean up all containers' information on it
          logger.error("worker:" + workerId + "is unhealthy, clean up all containers");
        }
        hasChangedLock.unlock();
        writeLock.unlock();
        logger.debug("unlock at 195");
      }


      @Override
      public void onCompleted() {

      }
    };
  }

  @Setter
  static class HeartBeatClient implements StreamObserver<HeartBeatResponse> {

    private static final Log logger = LogFactory.getLog(HeartBeatClient.class);
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
      this.hasChangedLock.lock();
      Lock workerLock = lock.writeLock();
      workerLock.lock();
      logger.debug("lock at 260");
      Worker worker = workers.get(workerId);
      worker.setStatus(Status.UNHEALTHY);
      for (String function : worker.getInstances().keySet()) {
        this.hasChanged.put(function, true);
      }
      worker.setInstances(new HashMap<>());
      workerLock.unlock();
      logger.debug("unlock at 268");
      this.hasChangedLock.unlock();
      logger.warn("worker " + workerId + " has completed");
      heartBeatRequestObserver.onCompleted();
      this.stop = true;
    }

    @Override
    public void onCompleted() {
      // change the state of the worker
//      Lock workerLock = lock.writeLock();
//      workerLock.lock();
//      Worker worker = workers.get(workerId);
//      worker.setStatus(Status.UNHEALTHY);
//      this.hasChangedLock.lock();
//      for (String function : worker.getInstances().keySet()) {
//        this.hasChanged.put(function, true);
//      }
//      worker.setInstances(new HashMap<>());
//      this.hasChangedLock.unlock();
//      logger.info("hasChanged unlock at heartBeat onCompleted");
//      workerLock.unlock();
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
    Worker worker = new Worker();
    worker.setIdentity(request.getId());
    worker.setAddr(request.getAddr());
    worker.setChannel(ManagedChannelBuilder.forTarget(request.getAddr()).usePlaintext().build());
    worker.setInstances(new HashMap<>());
    worker.setPreInstances(new ConcurrentHashMap<>());
    worker.setResources(new ConcurrentHashMap<>());
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    logger.debug("lock at 318");
    if (workers.get(request.getId()) != null) {
      // worker re register
      Worker oldWorker = workers.get(request.getId());
      // we must clear up the preInstances map at re register.
      oldWorker.setPreInstances(new ConcurrentHashMap<>());
      oldWorker.setResources(new ConcurrentHashMap<>());
      oldWorker.setStatus(Status.RUNNING);
      logger.info("request worker id:" + request.getId());
      heartbeats.execute(new Runnable() {
        @Override
        public void run() {
          ManagedChannel beatChannel = ManagedChannelBuilder.forTarget(request.getAddr())
              .usePlaintext()
              .build();
          oldWorker.getHeartbeatChannel().shutdownNow();
          oldWorker.setHeartbeatChannel(beatChannel);
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
          .setMsg("you has already registered")
          .setCode(RegisterResponse.Code.OK)
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
    logger.debug("unlock at 378");
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
      worker.initFunction(resource);
    } catch (InitFunctionException | ChannelException e) {
      logger.error(e);
      // todo handle exception
      return;
    }
    // write into the functionWorkerMap
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    logger.debug("lock at 426");
    List<String> workerList = functionWorkerMap.get(resource.funcName);
    if (workerList == null) {
      workerList = new ArrayList<>();
    }
    workerList.add(workerId);
    functionWorkerMap.put(resource.funcName, workerList);
    writeLock.unlock();
    logger.debug("unlock at 434");
  }

  public void DeleteFunction(String funcName) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    logger.debug("lock at 440");
    functionWorkerMap.remove(funcName);
    logger.debug("unlock at 442");
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
    logger.debug("lock at 462");
    Worker target = null;
    Long lowerBound = Long.MAX_VALUE;
    for (String workerId : this.workers.keySet()) {
      Worker worker = this.workers.get(workerId);
      if (worker.getStatus().equals(Status.UNHEALTHY)) {
        logger.info("worker is unhealthy:" + worker);
        continue;
      }
      Long total = worker.GetTotalInstances();
      if (total < lowerBound) {
        target = worker;
        lowerBound = total;
      } else if (total.longValue() == lowerBound.longValue()) {
        if(Math.random() > 0.5) {
          target = worker;
          continue;
        }
      }
    }
    readLock.unlock();
    logger.debug("unlock at 477");
    return target;
  }

  // CreateInstance 和 DeleteInstance 下面的实现是对不齐的，这里日后做更细致的考虑。
  public void CreateInstance(Resource resource, Integer targetNum) {
    if (!hasWorker()) {
      logger.warn("there are no workers");
      throw new NoWorkerException("there are no workers");
    }
    int increaseNum = (int) (targetNum - this.getInstancesNumByWithPreFunctionName(resource.funcName));
    logger.info("increase function: " + resource.funcName + " with result " + increaseNum);
    // choose the first one now, here we can add policy to choose
    for (int i = 0; i < increaseNum; ++i) {
      Worker target = chooseWorker(resource);
      if (target != null && target.getStatus().equals(Status.RUNNING)) {
        target.initFunction(resource);
        try {
          target.CreateContainer(resource);
        } catch (CreateContainerException e) {
          logger.error(e);
        }
      }
    }
  }

  // targetNum is an absolute num of a function
  public void DeleteInstance(Resource resource, Integer targetNum) {

    Lock readLock = lock.readLock();
    readLock.lock();
    logger.debug("lock at 508");
    logger.debug("delete container 2");
    Long oldTargetNum = getInstancesNumByWithPreFunctionName(resource.funcName);
    logger.debug("delete container 3");
    // now at this function, we should do not care about delete negative number instances.
    if (oldTargetNum.intValue() <= targetNum) {
      logger.info("oldTargetNum:" + oldTargetNum);
      logger.info("targetNum:" + targetNum);
      logger.info("there is no need to delete instances");
      readLock.unlock();
      logger.debug("unlock at 518");
      return;
    }

    long deleteNum = oldTargetNum - targetNum;
    // todo add choose policy here
    for (Worker worker : this.workers.values()) {
      logger.info("worker id:" + worker.getIdentity());
      Long size = worker.GetInstancesNumByFunctionName(resource.funcName);
      logger.info("worker instance size:" + size);
      size += worker.GetPreInstancesByFunctionName(resource.funcName);
      logger.info("worker instance add pre size:" + size);
      logger.info("deleteNum:" + deleteNum);
      try {
        if (deleteNum >= size) {
          // todo fix evil logic
          logger.debug("delete container 4");
          if (size == 0) {
            continue;
          }
          deleteNum -= size;
          worker.DeleteContainer(resource, 0);
          logger.debug("delete container 5");
        } else {
          logger.debug("delete container 8");
          worker.DeleteContainer(resource, Math.toIntExact(size - deleteNum));
          logger.debug("delete container 9");
          break;
        }
      } catch (ChannelException e) {
        e.printStackTrace();
      } catch (NoRuntimeException e) {
        e.printStackTrace();
      }
    }
    readLock.unlock();
    logger.debug("unlock at 554");
  }
}
