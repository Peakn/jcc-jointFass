package com.fc.springcloud.docker;

import com.google.common.util.concurrent.Striped;
import com.google.protobuf.ByteString;
import com.fc.springcloud.dto.Container;
import com.fc.springcloud.dto.Resource;
import com.fc.springcloud.exception.ContainerNotFoundException;
import com.fc.springcloud.exception.LoadCodeException;
import com.fc.springcloud.exception.NoSuchFunctionException;
import com.fc.springcloud.policy.Policy;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jointfaas.container.ContainerGrpc;
import jointfaas.container.ContainerGrpc.ContainerBlockingStub;
import jointfaas.container.InvokeRequest;
import jointfaas.container.InvokeResponse;
import jointfaas.container.LoadCodeRequest;
import jointfaas.container.LoadCodeResponse;
import jointfaas.container.LoadCodeResponse.Code;
import jointfaas.manager.ManagerGrpc;
import jointfaas.manager.ManagerGrpc.ManagerStub;
import jointfaas.manager.ManagerOuterClass.SyncRequest;
import jointfaas.manager.ManagerOuterClass.SyncResponse;
import lombok.Setter;
import lombok.SneakyThrows;

public class ContainerMaintainer {

  public static final Integer DEFAULT_RUNTIME_PORT = 50051;
  public static final Integer DEFAULT_INVOKE_PORT = 40041;
  private static final Logger logger = Logger.getLogger(ContainerMaintainer.class.getName());
  private final Client client;
  Striped<ReadWriteLock> stripedLock;
  Lock syncLock;
  private final String workerId;
  private Map<String, List<Container>> workingContainers;
  private Map<String, List<Container>> idleContainers;
  private ConcurrentHashMap<String, Resource> resources;
  private Map<String, Boolean> hasChanged;
  private ExecutorService syncThreadPool;
  private ManagedChannel managerChannel;

  @Setter
  static class SyncClient implements StreamObserver<SyncResponse> {

    private static final Logger logger = Logger.getLogger(SyncClient.class.getName());

    public SyncClient() {
    }

    @Override
    public void onNext(SyncResponse syncResponse) {
      logger.info(syncResponse.toString());
    }

    @Override
    public void onError(Throwable throwable) {
      logger.warning(throwable.getMessage());
    }

    @Override
    public void onCompleted() {
      logger.info("on completed");
    }
  }

  public ContainerMaintainer(Client client, final ManagedChannel managerChannel,
      final String workerId) {
    this.client = client;
    this.workerId = workerId;
    this.managerChannel = managerChannel;
    this.stripedLock = Striped.readWriteLock(100);
    this.syncLock = new ReentrantLock();
    this.workingContainers = new HashMap<>();
    this.idleContainers = new HashMap<>();
    this.resources = new ConcurrentHashMap<>();
    this.hasChanged = new HashMap<>();
    syncThreadPool = Executors.newFixedThreadPool(1);
  }

  @SneakyThrows
  public void InitWorkingContainers() {
    List<Container> containers = this.client.ListContainerHCloud();
    for(Container container: containers) {
      this.client.DeleteContainer(container.identity);
    }
  }

  public void startSync() {
    // create a thread to check hasChanged and sync info to Manager;
    // the information of a function is not delta(full)
    final ManagerStub syncClient = ManagerGrpc.newStub(managerChannel);
    syncThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        // iterate hasChanged
        try {
          SyncClient sc = new SyncClient();
          StreamObserver<SyncRequest> syncRequestObserver = syncClient.sync(sc);
          while (true) {
            syncLock.lock();
            Iterator<Entry<String, Boolean>> entries = hasChanged.entrySet().iterator();
            while (entries.hasNext()) {

              Entry<String, Boolean> entry = entries.next();
              String key = entry.getKey();
              if (entry.getValue()) {
                // should push
                Lock readLock = stripedLock.get(entry.getKey()).readLock();
                readLock.lock();
                List<String> instances = new ArrayList<>();
                List<Container> containers = workingContainers.get(key);
                for (Container container : containers) {
                  instances.add(container.ip + ":" + DEFAULT_INVOKE_PORT.toString());
                }
                logger.info("instances " + instances.toString());
                syncRequestObserver.onNext(SyncRequest.newBuilder()
                    .addAllInstances(instances)
                    .setFunctionName(key)
                    .setProvider("hcloud")
                    .setWorkerId(workerId)
                    .build());
                logger.info("client request over");
                readLock.unlock();
              }
            }
            hasChanged.clear();
            syncLock.unlock();
          }
        } catch (Exception e) {
          logger.warning(e.toString());
        }

      }
    });
  }

  // ClearContainer will clean container by identity
  public void clearContainer(Container container) {
    Lock writeLock = stripedLock.get(container.resource.getFuncName()).writeLock();
    writeLock.lock();
    client.DeleteContainer(container.identity);
    writeLock.unlock();
  }

  private ByteString InvokeRequest(String target, String funcName, ByteString payload) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext()
        .build();
    ContainerBlockingStub invokeClient = ContainerGrpc.newBlockingStub(channel);
    InvokeResponse response = invokeClient.invoke(InvokeRequest.newBuilder()
        .setPayload(payload)
        .setFuncName(funcName)
        .build());
    try {
      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, e.getMessage());
    }
    return response.getOutput();
  }

  // Invoke will expose to other package to invoke function and get the response
  public ByteString Invoke(String funcName, ByteString payload,
      Policy policy) {
    Lock readLock = stripedLock.get(funcName).readLock();
    readLock.lock();
    List<Container> scopedContainers = workingContainers.get(funcName);
    if (scopedContainers == null || scopedContainers.isEmpty()) {
      // AddContainer first and fast return error
      Resource resource = resources.get(funcName);
      // handle resource not found error
      // here we release readLock because of lock
      if (resource == null) {
        throw new NoSuchFunctionException("function is not existed", funcName);
      }
      readLock.unlock();
      this.addContainer(resource);
      return null;
    }
    // use policy arch
    Container chosenContainer = policy.GetContainer(scopedContainers);
    logger.info(chosenContainer.toString());

    readLock.unlock();
    return this
        .InvokeRequest(chosenContainer.ip + ":" + DEFAULT_RUNTIME_PORT.toString(),
            chosenContainer.resource.funcName,
            payload);
  }

  // AddContainer will create Container with specific Information
  private void addContainer(Resource resource) {
    logger.info("add container with resource:" + resource.toString());
    Lock writeLock = this.stripedLock.get(resource.getFuncName()).writeLock();
    writeLock.lock();
    Container container = client.CreateContainer(resource);
    List<Container> containers = null;
    containers = idleContainers.get(resource.funcName);
    if (containers == null) {
      containers = new ArrayList<>();
    }
    containers.add(container);
    logger
        .info("add container to " + resource.funcName + " with container id " + container.identity);
    idleContainers.put(resource.funcName, containers);
    writeLock.unlock();
  }

  private List<Container> chooseCleanTarget(Resource resource, Integer targetNum) {
    Lock writeLock = this.stripedLock.get(resource.getFuncName()).writeLock();
    writeLock.lock();
    List<Container> workingList = this.workingContainers.get(resource.funcName);
    if (workingList == null) {
      workingList = new ArrayList<>();
    }
    List<Container> idleList = this.idleContainers.get(resource.funcName);
    if (idleList == null) {
      idleList = new ArrayList<>();
    }
    if (targetNum >= workingList.size() + idleList.size()) {
      logger.info("there is no need to delete container");
      writeLock.unlock();
      return new ArrayList<>();
    }
    List<Container> targetList = new ArrayList<>();
    if(targetNum - workingList.size() > 0) {
      // all workingList will save.
      int reduceNum = workingList.size() + idleList.size() - targetNum;
      for (int i = 0; i < reduceNum; ++i) {
        targetList.add(idleList.get(i));
      }
      idleList = idleList.subList(reduceNum, idleList.size());
      this.idleContainers.put(resource.funcName, idleList);
    } else {
      // clean up idleList and delete some of workingList
      targetList.addAll(idleList);
      idleList.clear();
      int reduceNum = workingList.size() - targetNum;
      for (int i = 0; i < reduceNum; ++i) {
        targetList.add(workingList.get(i));
      }
      workingList = workingList.subList(reduceNum, workingList.size());
      this.workingContainers.put(resource.funcName, workingList);
    }
    syncLock.lock();
    logger.info("set function " + resource.funcName + " has changed");
    hasChanged.put(resource.funcName, true);
    syncLock.unlock();
    writeLock.unlock();
    return targetList;
  }

  private void ContainerLoadCode(Container container) {
    logger.info(container.identity + " is loading code with ip " + container.ip);
    ManagedChannel channel = ManagedChannelBuilder.forAddress(container.ip, DEFAULT_RUNTIME_PORT)
        .usePlaintext()
        .build();
    ContainerBlockingStub loadCodeClient = ContainerGrpc.newBlockingStub(channel);
    LoadCodeResponse response = loadCodeClient.loadCode(LoadCodeRequest.newBuilder()
        .setFuncName(container.getResource().getFuncName())
        .setUrl(container.getResource().getCodeUrI())
        .build());
    // handle response error
    if (response.getCode() != Code.OK) {
      try {
        channel.shutdown().awaitTermination(0, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, e.getMessage());
      }
      throw new LoadCodeException("container load code error", container.identity);
    }
    try {
      channel.shutdown().awaitTermination(0, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, e.getMessage());
    }
    logger.info(container.identity + " is loading code over with resp " + response.toString());
  }

  public void RegisterHandle(String id, String addr, String funcName, String runtime, long memory,
      long disk) {
    // todo the rest info for validation
    Lock writeLock = this.stripedLock.get(funcName).writeLock();
    writeLock.lock();
    List<Container> containers = idleContainers.get(funcName);
    if (containers == null) {
      // handle idleContainer is not existed error
      logger.info("container is null, error");
      writeLock.unlock();
      throw new ContainerNotFoundException("container is not found", id);
    }
    Iterator<Container> iterator = containers.iterator();
    // todo change list implementation => map
    while (iterator.hasNext()) {
      Container container = iterator.next();
      logger.info("container iterator id " + container.identity);
      // the id we get from client is the completed version ,so we just use startsWith to check.
      if (container.identity.startsWith(id)) {
        container.ip = addr;
        // find the idle container
        // in the new version the code uri is env.CODE_URI
//        try {
//          this.ContainerLoadCode(container);
//        } catch (LoadCodeException e) {
//          // handle loadCode exception
//          logger.log(Level.WARNING, e.getMessage() + " " + e.getContainerId());
//          throw e;
//        }
        iterator.remove();
        List<Container> target = workingContainers.get(funcName);
        if (target == null) {
          target = new ArrayList<>();
        }
        target.add(container);
        workingContainers.put(funcName, target);
        writeLock.unlock();
        // todo report the ip to manager
        syncLock.lock();
        logger.info("set function " + funcName + " has changed");
        hasChanged.put(funcName, true);
        syncLock.unlock();
        return;
      }
    }
    throw new ContainerNotFoundException("container is not found", id);
  }

  public void InitFunction(Resource resource) {
    resources.put(resource.funcName, resource);
  }

  private Boolean unpauseContainer(Resource resource, Integer targetNum) {
    Lock writeLock = this.stripedLock.get(resource.getFuncName()).writeLock();
    writeLock.lock();
    List<Container> pauseList = this.client.ListPausedContainers(resource);
    if (pauseList.size() == 0) {
      writeLock.unlock();
      return false;
    }

    for (Container container: pauseList) {
      // todo check targetNum
      this.client.UnpauseContainer(container.identity);
      List<Container> workingList = this.workingContainers.get(resource.getFuncName());
      if (workingList == null) {
        workingList = new ArrayList<>();
      }
      workingList.add(container);
      this.workingContainers.put(resource.getFuncName(), workingList);
      break;
    }
    syncLock.lock();
    logger.info("unpause container set function " + resource.getFuncName() + " has changed");
    hasChanged.put(resource.getFuncName(), true);
    syncLock.unlock();
    writeLock.unlock();
    return true;
  }

  public void CreateContainer(String funcName, Integer targetNum) {
    Lock readLock = stripedLock.get(funcName).readLock();
    readLock.lock();
    Resource resource = resources.get(funcName);
    if (resource == null) {
      throw new NoSuchFunctionException("function is not existed", funcName);
    }
    readLock.unlock();
    if (unpauseContainer(resource, targetNum)) {
    } else {
      addContainer(resource);
    }
  }

  public void DeleteContainer(String funcName, Integer tarNum) {
    Lock readLock = stripedLock.get(funcName).readLock();
    readLock.lock();
    Resource resource = resources.get(funcName);
    if (resource == null) {
      throw new NoSuchFunctionException("function is not existed", funcName);
    }
    readLock.unlock();
    deleteContainer(resource, tarNum);
  }

  // DeleteContainer will delete Container with specific Information
  @SneakyThrows
  private void deleteContainer(Resource resource, Integer targetNum) {
    logger.info("delete container with resource" + resource.toString());

    // The design is to prevent a case from happening
    // There is race risk if a container delete before report to Manager.
    // At the same time, a new-created runtime will reuse the IP which maybe another runtime with different function.
    // Others runtime will still call the IP, but the runtime is not the old one and then make mistakes.
    // Now sleep is also not a good fix for the problem, but it can get me to graduate.
    List<Container> targetList = chooseCleanTarget(resource, targetNum);
    Thread.sleep(100);
    for (Container container : targetList) {
      this.client.DeleteContainer(container.identity);
    }
  }

  public void PauseContainer(String funcName, int tarNum) {
    Lock readLock = stripedLock.get(funcName).readLock();
    readLock.lock();
    Resource resource = resources.get(funcName);
    if (resource == null) {
      throw new NoSuchFunctionException("function is not existed", funcName);
    }
    readLock.unlock();
    pauseContainer(resource, tarNum);
  }

  @SneakyThrows
  private void pauseContainer(Resource resource, Integer targetNum) {
    logger.info("pause container with resource" + resource.toString());

    // The design is to prevent a case from happening
    // There is race risk if a container delete before report to Manager.
    // At the same time, a new-created runtime will reuse the IP which maybe another runtime with different function.
    // Others runtime will still call the IP, but the runtime is not the old one and then make mistakes.
    // Now sleep is also not a good fix for the problem, but it can get me to graduate.
    List<Container> pauseList = chooseCleanTarget(resource, targetNum);
    Thread.sleep(100);
    for (Container container : pauseList) {
      this.client.PauseContainer(container.identity);
    }
  }

}


