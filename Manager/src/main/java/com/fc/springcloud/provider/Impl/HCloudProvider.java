package com.fc.springcloud.provider.Impl;

import com.fc.springcloud.provider.PlatformProvider;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jointfaas.worker.InitFunctionRequest;
import jointfaas.worker.InitFunctionResponse;
import jointfaas.worker.InitFunctionResponse.Code;
import jointfaas.worker.WorkerGrpc;
import jointfaas.worker.WorkerGrpc.WorkerBlockingStub;
import lombok.Data;
import lombok.Getter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.stereotype.Component;

@Component
public class HCloudProvider implements PlatformProvider {

  private static final Log logger = LogFactory.getLog(HCloudProvider.class);

  @Getter
  static class InitFunctionException extends RuntimeException {

    private String identity;

    public InitFunctionException(String message, String identity) {
      super(message);
      this.identity = identity;
    }
  }

  @Data
  static class Worker {

    private String identity;
    private String addr;
    private ManagedChannel channel;

    public void initFunction(String funcName, String image, String runtime, String codeURI,
        int memorySize, int timeout) {
      if (channel.isShutdown() || channel.isTerminated()) {

      }
      WorkerBlockingStub initFunctionClient = WorkerGrpc
          .newBlockingStub(this.channel);
      InitFunctionResponse resp = initFunctionClient
          .initFunction(InitFunctionRequest.newBuilder()
              .setFuncName(funcName)
              .setImage(image)
              .setRuntime(runtime)
              .setCodeURI(codeURI)
              .setMemorySize(memorySize)
              .setTimeout(timeout)
              .build());
      if (resp.getCode() != Code.OK) {
        throw new InitFunctionException(resp.getMsg(), this.identity);
      }
    }
  }

  private Map<String, Worker> workers;
  private ReadWriteLock lock;

  public HCloudProvider() {
    this.workers = new HashMap<>();
    this.lock = new ReentrantReadWriteLock();
  }

  @Override
  public Object CreateService(String serviceName) {
    return null;
  }

  // CreateFunction will be called after the code has been storage.
  @Override
  public Object CreateFunction(String functionName, String codeUrl, String runTimeEnvir,
      String handler) {
    // broadcast to all worker
    Lock readLock = this.lock.readLock();
    readLock.lock();
    String image = "";
    switch (runTimeEnvir) {
      case "Java8" : {
        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-java:v1.0";
        break;
      }
      case "Python3": {
        image = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-python:v1.0";
        break;
      }
      default :{
      }
    }
    for (Worker worker : this.workers.values()) {
      // todo hard code parameter
      try {
        worker.initFunction(functionName, image, runTimeEnvir, codeUrl, 100, 1);
      } catch(InitFunctionException e) {
        logger.error(e);
        return null;
      }
    }
    return null;
  }

  @Override
  public Object InvokeFunction(String functionName, String jsonObject) {
    return null;
  }

  @Override
  public Object UpdateFunction(String functionName, String codeDir, String runTimeEnvir,
      String handler) throws IOException {
    logger.warn("hcloud has not supported update function");
    return null;
  }

  @Override
  public Object DeleteFunction(String functionName) {
    return null;
  }

  @Override
  public Object ListFunction() {
    return null;
  }

}
