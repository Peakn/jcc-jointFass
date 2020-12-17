package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.mesh.Cluster;
import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.provider.Impl.hcloud.exception.CreateException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeFunctionException;
import com.fc.springcloud.provider.Impl.hcloud.exception.RuntimeEnvironmentException;
import com.fc.springcloud.provider.PlatformProvider;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class HCloudProvider implements PlatformProvider {

  private static final Log logger = LogFactory.getLog(HCloudProvider.class);

  private ReadWriteLock readWriteLock;
  private WorkerMaintainerServer workerMaintainer;
  private Map<String, Resource> functions;
  private Map<String, BlockingQueue<Cluster>> clusterSyncCollection;
  private Lock clusterSyncCollectionLock;
  private Map<String, Boolean> clusterHasChanged;
  private Lock hasChangedLock;
  private ExecutorService backend;

  @Value("${mesh.use}")
  private boolean enableInject;

  @Autowired
  private MeshClient meshInjector;

  public HCloudProvider() {
    this.functions = new HashMap<>();
    this.readWriteLock = new ReentrantReadWriteLock();
    this.clusterHasChanged = new HashMap<>();
    this.hasChangedLock = new ReentrantLock();
    this.workerMaintainer = new WorkerMaintainerServer(30347, this.clusterHasChanged,
        this.hasChangedLock);
    this.backend = Executors.newFixedThreadPool(3);
    this.clusterSyncCollection = new HashMap<>();
    this.clusterSyncCollectionLock = new ReentrantLock();
    // for connection with worker
    backend.execute(new Runnable() {
      @Override
      public void run() {
        try {
          workerMaintainer.start();
          workerMaintainer.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
          logger.fatal("worker maintainer err:" + e.getMessage());
        }
      }
    });
    // for sync information to mesh center
    backend.execute(new Runnable() {
      @SneakyThrows
      @Override
      public void run() {
        while (true) {
          hasChangedLock.lock();
          logger.info("cluster changed");
          logger.info(clusterHasChanged);
          for (String functionName : clusterHasChanged.keySet()) {
            if (clusterHasChanged.get(functionName)) {
              clusterSyncCollectionLock.lock();
              BlockingQueue<Cluster> downstream = clusterSyncCollection.get(functionName);
              clusterSyncCollectionLock.unlock();
              if (downstream == null) {
                logger.warn("function " + functionName + " does not have the sync queue");
                continue;
              }
              List<String> instances = workerMaintainer
                  .GetInstanceByFunctionName(functionName);
              Cluster cluster = new Cluster(instances, "hcloud", functionName);
              logger.info("download stream add cluster: " + cluster.toString());
              downstream.add(cluster);
            }
          }
          clusterHasChanged.clear();
          hasChangedLock.unlock();
          Thread.sleep(1000);
        }
      }
    });

    // todo for auto scaling
    // now will be the action after an application is create
//    backend.execute(new Runnable() {
//      @Override
//      public void run() {
//      }
//    });
  }

  public void stop() throws InterruptedException {
    this.workerMaintainer.stop();
  }


  // CreateFunction will be called after the code has been storage.
  @Override
  public void CreateFunction(String funcName, String codeURI, String runtime) {
    // just write into memory;
    String image = "";
    switch (runtime) {
      case "java8": {
        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-java:v1.0";
        break;
      }
      case "python3": {
        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-python:v2.0";
        break;
      }
      default: {
        throw new RuntimeEnvironmentException("runtime has not been supported at hcloud", runtime);
      }
    }
    // todo hard code at memorySize and timeout
    Resource resource = new Resource(funcName, image, runtime, codeURI, 128, 60);
    Lock writeLock = readWriteLock.writeLock();
    writeLock.lock();
    if (functions.get(funcName) == null) {
      functions.put(funcName, resource);
      writeLock.unlock();
    } else {
      writeLock.unlock();
      throw new CreateException("function " + funcName + " has already been created");
    }
    // todo set some optional parameter
    BlockingQueue<Cluster> functionSyncQueue = new ArrayBlockingQueue<>(10);
    clusterSyncCollectionLock.lock();
    if (clusterSyncCollection.get(funcName) != null) {
      logger.info("function " + funcName + "has a sync queue");
    } else {
      clusterSyncCollection.put(funcName, functionSyncQueue);
      meshInjector.syncFunctionInfo(funcName, "hcloud.com", "hcloud.com", null,
          clusterSyncCollection.get(funcName));
    }
    clusterSyncCollectionLock.unlock();
  }

  @Override
  public String InvokeFunction(String funcName, String jsonString) {
    // if worker is not found, just return
    if (!workerMaintainer.hasWorker()) {
      throw new InvokeFunctionException("No registered worker is found");
    }
    // check function first, if function not found ,just return
    // find a worker logic is in the WorkerMaintainer
    Resource resource = functions.get(funcName);
    if (resource == null) {
      throw new InvokeFunctionException("Can not find the resource");
    }
    try {
      byte[] output = workerMaintainer.invokeFunction(resource, jsonString.getBytes());
      return new String(output);
    } catch (InvokeException e) {
      logger.fatal(e.getMessage());
      throw e;
    }
  }

  @Override
  public void UpdateFunction(String funcName, String codeDir, String runtime) throws IOException {
    logger.warn("hcloud has not supported update function");
  }

  @Override
  public void DeleteFunction(String funcName) {
    Lock writeLock = readWriteLock.writeLock();
    writeLock.lock();
    functions.remove(funcName);
    workerMaintainer.DeleteFunction(funcName);
    writeLock.unlock();
  }

  @Override
  public Object ListFunction() {
    return null;
  }

  private void CreateContainer(String funcName) {
    // find a resource
    Lock lock = readWriteLock.readLock();
    lock.lock();
    Resource resource = this.functions.get(funcName);
    if (resource == null) {
      throw new RuntimeException("function " + funcName + " resource not found");
    }
    this.workerMaintainer.CreateInstance(resource);
    lock.unlock();
  }

  public void InitWorkerLoad(List<String> steps) {
    for (String funcName : steps) {
      this.CreateContainer(funcName);
    }
  }
}
