package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.provider.Impl.hcloud.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloud.exception.CreateContainerException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloud.exception.NoRuntimeException;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import jointfaas.worker.CreateContainerRequest;
import jointfaas.worker.CreateContainerResp;
import jointfaas.worker.DeleteContainerRequest;
import jointfaas.worker.DeleteContainerResp;
import jointfaas.worker.InitFunctionRequest;
import jointfaas.worker.InitFunctionResponse;
import jointfaas.worker.InitFunctionResponse.Code;
import jointfaas.worker.InvokeRequest;
import jointfaas.worker.InvokeResponse;
import jointfaas.worker.WorkerGrpc;
import jointfaas.worker.WorkerGrpc.WorkerBlockingStub;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

@Data
@Getter
@Setter
public class Worker {

  @Getter
  public enum Status {
    RUNNING(0, "RUNNING"),
    UNHEALTHY(1, "UNHEALTHY");
    private int code;
    private String message;

    Status(int code, String message) {
      this.code = code;
      this.message = message;
    }
  }


  private static final Log logger = LogFactory.getLog(Worker.class);
  private String identity;
  private String addr;
  private ManagedChannel channel;
  private ManagedChannel heartbeatChannel;
  private Status status = Status.RUNNING;
  private Map<String, List<String>> instances;
  private ConcurrentHashMap<String, Resource> resources;
  private ConcurrentHashMap<String, Long> preInstances;

  public void initFunction(Resource resource) {

    if (channel.isShutdown() || channel.isTerminated()) {
      throw new ChannelException("the channel is terminated or shutdown", identity);
    }

    if (resources.get(resource.funcName) != null && resources.get(resource.funcName)
        .equals(resource)) {
      return;
    }

    WorkerBlockingStub initFunctionClient = WorkerGrpc
        .newBlockingStub(channel);
    InitFunctionResponse resp = initFunctionClient
        .initFunction(InitFunctionRequest.newBuilder()
            .setFuncName(resource.funcName)
            .setImage(resource.image)
            .setRuntime(resource.runtime)
            .setCodeURI(resource.codeURI)
            .setMemorySize(resource.memorySize)
            .setTimeout(resource.timeout)
            .build());
    if (resp.getCode() != Code.OK) {
      throw new InitFunctionException(resp.getMsg(), identity);
    }
    this.resources.put(resource.funcName, resource);
  }

  public byte[] invoke(String funcName, byte[] input) {
    if (channel.isTerminated() || channel.isShutdown()) {
      throw new ChannelException("the channel is terminated or shutdown", identity);
    }
    WorkerBlockingStub client = WorkerGrpc.newBlockingStub(channel);
    InvokeResponse resp = client.invoke(InvokeRequest.newBuilder()
        .setName(funcName)
        .setPayload(ByteString.copyFrom(input))
        .build());
    if (resp.getCode() == InvokeResponse.Code.OK) {
      return resp.getOutput().toByteArray();
    } else if (resp.getCode() == InvokeResponse.Code.RETRY) {
      throw new InvokeException("there is no runtime that works about function:" + funcName,
          InvokeResponse.Code.RETRY);
    } else if (resp.getCode() == InvokeResponse.Code.NO_SUCH_FUNCTION) {
      logger.warn("no such function at worker " + identity);
      throw new InvokeException("worker functions and manager functions are inconsistent",
          InvokeResponse.Code.NO_SUCH_FUNCTION);
    } else if (resp.getCode() == InvokeResponse.Code.RUNTIME_ERROR) {
      logger.warn("runtime error at worker" + identity);
      throw new InvokeException("runtime error at worker " + identity,
          InvokeResponse.Code.RUNTIME_ERROR);
    }
    return null;
  }

  public void CreateContainer(Resource resource) {
    WorkerBlockingStub client = WorkerGrpc.newBlockingStub(channel);
    Long result = this.preInstances
        .computeIfPresent(resource.funcName, (key, value) -> value + 1);
    if (result == null) {
      this.preInstances.put(resource.funcName, 1L);
    }
    if (resources.get(resource.funcName) != null && resources.get(resource.funcName)
        .equals(resource)) {
      CreateContainerResp resp = client
          .createContainer(CreateContainerRequest.newBuilder()
              .setFuncName(resource.funcName)
              .build());
      logger.info("create container with function:" + resource.funcName);
    } else {
      throw new CreateContainerException("resource is null", this.identity);
    }
  }

  public void DeleteContainer(Resource resource, Integer targetNum) {
    // check first
    if (channel.isTerminated() || channel.isShutdown()) {
      throw new ChannelException("the channel is terminated or shutdown", identity);
    }
    logger.info(this.instances);
    logger.info(resource.funcName);
    int size = this.instances.get(resource.funcName).size();
    logger.debug("delete container 5");
    WorkerBlockingStub client = WorkerGrpc.newBlockingStub(channel);
    logger.info("before send request:" + resource.funcName);
    DeleteContainerResp resp = client
        .deleteContainer(DeleteContainerRequest.newBuilder()
            .setFuncName(resource.funcName)
            .setNum(targetNum)
            .build());
    logger.info("after send request:" + resource.funcName);
    if (resp.getCode().equals(DeleteContainerResp.Code.OK)) {
      logger.debug("delete container 6");

      this.preInstances
          .put(resource.funcName, (long) (targetNum - size));
      logger.debug("delete container 7");
    } else if (resp.getCode().equals(DeleteContainerResp.Code.NO_RUNTIME)) {
      throw new NoRuntimeException("worker has no runtime", this.identity);
    } else if (resp.getCode().equals(DeleteContainerResp.Code.NO_SUCH_FUNCTION)) {
      logger.info("delete container return no such function:" + resource.funcName);
    }
  }

  public Integer AggregateInstancesByFunctionName() {
    return 0;
  }

  public Long GetTotalInstances() {
    long count = 0;
    LongAdder longAdder = new LongAdder();
    for (String funcName : instances.keySet()) {
      List<String> funcInstances = instances.get(funcName);
      count += funcInstances.size();
    }
    preInstances.forEachValue(Long.MAX_VALUE, longAdder::add);
    return count + longAdder.longValue();
  }

  public Long GetPreInstancesByFunctionName(String functionName) {
    return this.preInstances.getOrDefault(functionName, 0L);
  }

  private List<String> getInstancesByFunctionName(String functionName) {
    return this.instances.getOrDefault(functionName, new ArrayList<>());
  }

  public List<String> GetInstancesByFunctionName(String functionName) {
    return getInstancesByFunctionName(functionName);
  }

  public Long GetInstancesNumByFunctionName(String functionName) {
    return (long) getInstancesByFunctionName(functionName).size();
  }

  public void SyncInstances(String functionName, List<String> instances) {
    int size = this.instances.getOrDefault(functionName, new ArrayList<>()).size();
    Long preSize = this.preInstances.getOrDefault(functionName, 0L);
    int diff = size - instances.size();
    logger.info("sync into worker object: " + instances);
    logger.info("preInstances:" + preSize);
    if (preSize == 0L) {
      this.preInstances.put(functionName, 0L);
    } else {
      this.preInstances.put(functionName, preSize + diff);
    }
    this.instances.put(functionName, instances);
  }
}
