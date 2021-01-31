package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.config.HCloudConfig;
import com.fc.springcloud.mapping.FunctionMapper;
import com.fc.springcloud.mesh.Cluster;
import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.dto.GatewayEvent;
import com.fc.springcloud.pojo.dto.ScheduleEvent;
import com.fc.springcloud.pojo.query.FunctionQuery;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeFunctionException;
import com.fc.springcloud.provider.Impl.hcloud.exception.WorkerNotFoundException;
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

  private class FunctionManager {

    private final Log logger = LogFactory.getLog(FunctionManager.class);

    public Resource get(String funcName) {
      FunctionDo functionDo = HCloudProvider.this.functionMapper.selectByFunctionName(funcName);
      String image = "";
      switch (functionDo.getRunEnv()) {
        case python3: {
          image = PYTHON_RUNTIME;
          break;
        }
        case java8: {
          image = JAVA_RUNTIME;
          break;
        }
        case nodejs10: {
          image = NODEJS10_RUNTIME;
          break;
        }
        default: {
          logger.warn("unsupported runtime");
          System.exit(-1);
        }
      }
      String functionId = functionDo.getFunctionId();
      String path = "http://" + serverAddress + ":" + serverPort + "/functionFile/" + functionId;
      return new Resource(functionDo.getFunctionName(), image,
          functionDo.getRunEnv().getDisplayName(), path, functionDo.getMemorySize(),
          functionDo.getTimeout());
    }
  }

  private class ScheduleEventHandler implements Runnable {

    private final Log logger = LogFactory.getLog(ScheduleEventHandler.class);
    private final ScheduleEvent event;

    ScheduleEventHandler(ScheduleEvent event) {
      this.event = event;
    }

    @Override
    public void run() {
      switch (event.getAction()) {
        case create: {
          try {
            CreateContainer(event.getFunctionName(), event.getTarget());
          } catch (Exception e) {
            logger.info("error at hcloud create container");
            e.printStackTrace();
          }
          break;
        }
        case delete: {
          // todo deleteContainer
          try {
            DeleteContainer(event.getFunctionName(), event.getTarget());
          } catch (Exception e) {
            logger.info("error at hcloud delete container");
            e.printStackTrace();
          }
          logger.info("receive delete event:" + event);
        }
      }
    }
  }

  private static final Log logger = LogFactory.getLog(HCloudProvider.class);

  private ReadWriteLock readWriteLock;
  private WorkerMaintainerServer workerMaintainer;
  private FunctionManager functions;
  private Map<String, BlockingQueue<Cluster>> clusterSyncCollection;
  private Lock clusterSyncCollectionLock;
  private Map<String, Boolean> clusterHasChanged;
  private Lock hasChangedLock;
  private ExecutorService backend;
  private Scheduler scheduler;
  private BlockingQueue<ScheduleEvent> scheduleEvents;
  private BlockingQueue<GatewayEvent> gatewayEvents;

  @Value("${mesh.runtime.java:registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-java:v1.0}")
  private String JAVA_RUNTIME;

  @Value("${mesh.runtime.python:registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-python:v3.0}")
  private String PYTHON_RUNTIME;

  @Value("${mesh.runtime.nodejs:registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-javascript:v4.0}")
  private String NODEJS10_RUNTIME;

  @Autowired
  private MeshClient meshInjector;

  @Autowired
  private HCloudConfig config;

  @Autowired
  private FunctionMapper functionMapper;

  @Value("${server.exportAddress}")
  private String serverAddress;

  @Value("${server.port}")
  private String serverPort;

  @Autowired
  private PrometheusResource prometheusResource;

  public HCloudProvider() {
    this.functions = new FunctionManager();
    this.readWriteLock = new ReentrantReadWriteLock();
    this.clusterHasChanged = new HashMap<>();
    this.hasChangedLock = new ReentrantLock();
    this.gatewayEvents = new ArrayBlockingQueue<GatewayEvent>(100);
    this.workerMaintainer = new WorkerMaintainerServer(30347, this.clusterHasChanged,
        this.hasChangedLock, this.gatewayEvents);
    this.backend = Executors.newFixedThreadPool(4);
    this.clusterSyncCollection = new HashMap<>();
    this.clusterSyncCollectionLock = new ReentrantLock();
    this.scheduleEvents = new ArrayBlockingQueue<ScheduleEvent>(100);
  }

  public void Start() {
    // todo re-create function from the database
    List<FunctionDo> functions = this.functionMapper.listFunctionByPages(new FunctionQuery());
    for (FunctionDo func : functions) {
      CreateFunction(func.getFunctionName(),
          "http://" + serverAddress + ":" + serverPort + "/functionFile/" + func.getFunctionId(),
          func.getRunEnv().getDisplayName());
    }
    this.scheduler = new Scheduler(this.prometheusResource.Register(), this.scheduleEvents,
        this.gatewayEvents, this);
    this.prometheusResource.Start();
    logger.info("hcloud provider start");
    // for connection with workers
    backend.execute(new Runnable() {
      @Override
      public void run() {
        try {
          workerMaintainer.Start();
          workerMaintainer.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
          logger.fatal("worker maintainer err:" + e.getMessage());
        }
      }
    });

    logger.info("mesh.use:" + config.meshEnable);
    // for sync information to mesh center
    if (config.meshEnable) {
      backend.execute(new Runnable() {
        @SneakyThrows
        @Override
        public void run() {
          while (true) {
            hasChangedLock.lock();
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
            Thread.sleep(100);
          }
        }
      });
    }

    // todo for auto scaling
    if (config.meshEnable) {
      scheduler.Start();
    }

    if (config.meshEnable) {
      backend.execute(new Runnable() {
        @SneakyThrows
        @Override
        public void run() {
          ExecutorService workerPool = Executors.newCachedThreadPool();
          while (true) {
            ScheduleEvent event = scheduleEvents.take();
            workerPool.execute(new ScheduleEventHandler(event));
          }
        }
      });
    }
  }

  public void Stop() throws InterruptedException {
    this.workerMaintainer.stop();
  }


  // CreateFunction will be called after the code has been storage.
  @Override
  public void CreateFunction(String funcName, String codeURI, String runtime) {
    // just write into memory;
//    String image = "";
//    switch (runtime) {
//      case "java8": {
//        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-java:v1.0";
//        break;
//      }
//      case "python3": {
//        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-python:v2.0";
//        break;
//      }
//      default: {
//        throw new RuntimeEnvironmentException("runtime has not been supported at hcloud", runtime);
//      }
//    }
    // todo hard code at memorySize and timeout
    // Resource resource = new Resource(funcName, image, runtime, codeURI, 128, 60);

    // todo set some optional parameter
    BlockingQueue<Cluster> functionSyncQueue = new ArrayBlockingQueue<>(10);
    clusterSyncCollectionLock.lock();
    if (clusterSyncCollection.get(funcName) != null) {
      logger.info("function " + funcName + "has a sync queue");
    } else {
      clusterSyncCollection.put(funcName, functionSyncQueue);
      String url = "http://"+ config.externalGatewayLocation + config.routerPrefix + funcName;
      String internalUrl = "http://"+ config.internalGatewayLocation + config.routerPrefix + funcName;
      meshInjector.syncFunctionInfo(funcName, internalUrl, url, null,
          clusterSyncCollection.get(funcName), "hcloud");
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
    // todo delete instance in all workers
  }

  @Override
  public Object ListFunction() {
    return null;
  }

  public void CreateContainer(String funcName, Integer targetNum) {
    // find a resource
    Lock lock = readWriteLock.readLock();
    lock.lock();
    Resource resource = this.functions.get(funcName);
    if (resource == null) {
      throw new RuntimeException("function " + funcName + " resource not found");
    }
    lock.unlock();
    try {
      this.workerMaintainer.CreateInstance(resource, targetNum);
    } catch (WorkerNotFoundException e) {
      logger.warn(e);
    }
  }

  public void DeleteContainer(String funcName, Integer targetNum) {
    // find a resource
    logger.debug("delete container 0");
    Lock lock = readWriteLock.readLock();
    lock.lock();
    Resource resource = this.functions.get(funcName);
    if (resource == null) {
      throw new RuntimeException("function " + funcName + " resource not found");
    }
    lock.unlock();
    try {
      logger.debug("delete container 1");
      this.workerMaintainer.DeleteInstance(resource, targetNum);
    } catch (WorkerNotFoundException e) {
      logger.warn(e);
    }
  }

  public void InitWorkerLoad(List<String> steps) {
    for (String funcName : steps) {
      this.CreateContainer(funcName, 1);
    }
  }
}
