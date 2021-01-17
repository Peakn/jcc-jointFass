package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.provider.Impl.hcloud.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.Map;
import jointfaas.worker.CreateContainerRequest;
import jointfaas.worker.CreateContainerResp;
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

  public void initFunction(String funcName, String image, String runtime, String codeURI,
      int memorySize, int timeout) {
    if (channel.isShutdown() || channel.isTerminated()) {
      throw new ChannelException("the channel is terminated or shutdown", identity);
    }
    WorkerBlockingStub initFunctionClient = WorkerGrpc
        .newBlockingStub(channel);
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
      throw new InitFunctionException(resp.getMsg(), identity);
    }
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
      throw new InvokeException("there is no runtime that works about function:" + funcName, InvokeResponse.Code.RETRY);
    } else if (resp.getCode() == InvokeResponse.Code.NO_SUCH_FUNCTION) {
      logger.warn("no such function at worker " + identity);
      throw new InvokeException("worker functions and manager functions are inconsistent", InvokeResponse.Code.NO_SUCH_FUNCTION);
    } else if (resp.getCode() == InvokeResponse.Code.RUNTIME_ERROR) {
      logger.warn("runtime error at worker" + identity);
      throw new InvokeException("runtime error at worker " + identity, InvokeResponse.Code.RUNTIME_ERROR);
    }
    return null;
  }

  public void CreateContainer(Resource resource) {
    WorkerBlockingStub client = WorkerGrpc.newBlockingStub(channel);
    CreateContainerResp resp = client
        .createContainer(CreateContainerRequest.newBuilder()
            .setFuncName(resource.funcName)
            .build());
    logger.info("create container with function:" + resource.funcName);
  }

  public Integer AggregateInstancesByFunctionName() {
    return 0;
  }

  public Integer GetTotalInstances() {
    int count = 0;
    for (String funcName : instances.keySet()) {
      List<String> funcInstances = instances.get(funcName);
      count += funcInstances.size();
    }
    return count;
  }
}
