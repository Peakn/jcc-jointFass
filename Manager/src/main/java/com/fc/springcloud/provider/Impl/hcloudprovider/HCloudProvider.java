package com.fc.springcloud.provider.Impl.hcloudprovider;

import com.fc.springcloud.provider.Impl.hcloudprovider.exception.CreateException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InvokeFunctionException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.RuntimeEnvironmentException;
import com.fc.springcloud.provider.PlatformProvider;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.stereotype.Component;

@Component
public class HCloudProvider implements PlatformProvider {

  private static final Log logger = LogFactory.getLog(HCloudProvider.class);

  private ReadWriteLock readWriteLock;
  private WorkerMaintainerServer workerMaintainer;
  private Map<String, Resource> functions;
  private ExecutorService backend;
  public HCloudProvider() {
    functions = new HashMap<>();
    readWriteLock = new ReentrantReadWriteLock();
    workerMaintainer = new WorkerMaintainerServer(7777);
    backend = Executors.newFixedThreadPool(1);
    backend.execute(new Runnable() {
      @Override
      public void run() {
        try {
          workerMaintainer.start();
          workerMaintainer.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
          logger.fatal(e.getMessage());
        }
      }
    });
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
        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-python:v1.0";
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
  }

  @Override
  public String InvokeFunction(String funcName, String jsonString) {
    // if worker is not found, just return
    if (!workerMaintainer.hasWorker()){
      throw new InvokeFunctionException("No registered worker is found");
    }
    // check function first, if function not found ,just return
    // find a worker logic is in the WorkerMaintainer
    Resource resource = functions.get(funcName);
    if (resource == null){
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


}
