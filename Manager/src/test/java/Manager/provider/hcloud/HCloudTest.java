package Manager.provider.hcloud;


import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

import com.fc.springcloud.provider.Impl.hcloudprovider.HCloudProvider;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.CreateException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.RuntimeEnvironmentException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import jointfaas.manager.ManagerGrpc;
import jointfaas.manager.ManagerGrpc.ManagerBlockingStub;
import jointfaas.manager.ManagerOuterClass;
import jointfaas.manager.ManagerOuterClass.RegisterResponse.Code;
import jointfaas.worker.InitFunctionRequest;
import jointfaas.worker.InitFunctionResponse;
import jointfaas.worker.InvokeRequest;
import jointfaas.worker.InvokeResponse;
import jointfaas.worker.MetricsRequest;
import jointfaas.worker.MetricsResponse;
import jointfaas.worker.RegisterRequest;
import jointfaas.worker.RegisterResponse;
import jointfaas.worker.ResetRequest;
import jointfaas.worker.ResetResponse;
import jointfaas.worker.WorkerGrpc.WorkerImplBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HCloudTest {

  private static final Log logger = LogFactory.getLog(HCloudTest.class);
  HCloudProvider hCloudProvider;

  ManagedChannel channel;

  ManagedChannel workerChannel;

  private final String workerId = "1";

  String testFuncName = "test-func";
  String testCodeURI = "1";
  String testRuntime = "java";

  Server workerServer;
  WorkerImplBase workerServerImpl = mock(WorkerImplBase.class, delegatesTo(
      new WorkerImplBase() {
        @Override
        public void invoke(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
          // todo
        }

        @Override
        public void register(RegisterRequest request,
            StreamObserver<RegisterResponse> responseObserver) {
          super.register(request, responseObserver);
        }

        @Override
        public void reset(ResetRequest request, StreamObserver<ResetResponse> responseObserver) {
          super.reset(request, responseObserver);
        }

        @Override
        public void initFunction(InitFunctionRequest request,
            StreamObserver<InitFunctionResponse> responseObserver) {
          // todo
        }

        @Override
        public void metrics(MetricsRequest request,
            StreamObserver<MetricsResponse> responseObserver) {
          super.metrics(request, responseObserver);
        }
      }
  ));

  @Before
  public void setUp() throws IOException, InterruptedException {
    hCloudProvider = new HCloudProvider();
    Thread.sleep(2000); // wait for manager server start.
    String serverName = InProcessServerBuilder.generateName();
    channel = ManagedChannelBuilder.forTarget("127.0.0.1:7777").usePlaintext().build();
    workerServer = InProcessServerBuilder
        .forName(serverName).directExecutor().addService(workerServerImpl).build().start();
    workerChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    ManagerBlockingStub client = ManagerGrpc.newBlockingStub(channel);
    ManagerOuterClass.RegisterResponse resp = client
        .register(ManagerOuterClass.RegisterRequest.newBuilder()
            .setAddr("127.0.0.1:" + String.valueOf(8080)) // unused port
            .setId(workerId)
            .build());
    if (!resp.getCode().equals(Code.OK)) {
      System.exit(-1);
    }
  }

  @org.junit.jupiter.api.Test
  public void testCreateFunction() {
    // handler is deprecated paramete
    this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime);
    try {
      this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime);
    } catch (CreateException e) {
      Assert.assertEquals(1, 1);
      return;
    }
    Assert.assertEquals(1, 0);
  }

  @org.junit.jupiter.api.Test
  public void testCreateErrorRuntimeFunction() {
    // handler is deprecated parameter
    try {
      this.hCloudProvider
          .CreateFunction(testFuncName, testCodeURI, testRuntime + "1");
    } catch (RuntimeEnvironmentException e) {
      Assert.assertEquals(1, 1);
      return;
    }
    Assert.assertEquals(1, 0);
  }

  @Test
  public void testDeleteFunction() {
    this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime + "1");
    this.hCloudProvider.DeleteFunction(testFuncName);
  }


  @After
  public void tearDown() throws InterruptedException {
    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
    hCloudProvider.stop();
  }
}
