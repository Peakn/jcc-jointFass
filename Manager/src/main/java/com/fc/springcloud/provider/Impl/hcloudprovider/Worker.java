package com.fc.springcloud.provider.Impl.hcloudprovider;

import com.fc.springcloud.provider.Impl.hcloudprovider.exception.ChannelException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InitFunctionException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InvokeException;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
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

  private static final Log logger = LogFactory.getLog(Worker.class);
  private String identity;
  private String addr;
  private ManagedChannel channel;
  private ManagedChannel heartbeatChannel;

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
      throw new InvokeException("there is no runtime that works", InvokeResponse.Code.RETRY);
    } else if (resp.getCode() == InvokeResponse.Code.NO_SUCH_FUNCTION) {
      logger.warn("no such function at worker " + identity);
      throw new InvokeException("worker functions and manager functions are inconsistent", InvokeResponse.Code.NO_SUCH_FUNCTION);
    } else if (resp.getCode() == InvokeResponse.Code.RUNTIME_ERROR) {
      logger.warn("runtime error at worker" + identity);
      throw new InvokeException("runtime error at worker " + identity, InvokeResponse.Code.RUNTIME_ERROR);
    }
    return null;
  }
}
