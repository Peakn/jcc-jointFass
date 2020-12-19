package Manager.provider.hcloud;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

import com.fc.springcloud.provider.Impl.hcloud.Resource;
import com.fc.springcloud.provider.Impl.hcloud.Worker;
import com.fc.springcloud.provider.Impl.hcloud.WorkerMaintainerServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WorkerMaintainerServerTest {

  private static final Log logger = LogFactory.getLog(WorkerMaintainerServerTest.class);

  Map<String, Boolean> hasChanged;
  WorkerMaintainerServer maintainerServer;
  ManagedChannel channel;
  ManagedChannel workerChannel;
  Server workerServer;
  ExecutorService backend;

  final String workerId = "1";
  final String testFuncName = "test-func";
  final String testRetryFuncName = "test-retry";
  final String testRuntimeErrorFuncName = "test-runtime-error";
  final String testCodeURI = "1";
  final String testRuntime = "java";
  final String errorImage = "errorImage";
  final String defaultImage = "image";
  Resource testResource = new Resource(testFuncName, "", testRuntime, testCodeURI, 100, 1);
  WorkerImplBase workerServerImpl = mock(WorkerImplBase.class, delegatesTo(
      new WorkerImplBase() {
        @Override
        public void invoke(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
          if (request.getName().equals(testFuncName)) {
            responseObserver.onNext(InvokeResponse.newBuilder()
                .setCode(InvokeResponse.Code.OK)
                .setOutput(request.getPayload())
                .build());
          } else if (request.getName().equals(testRuntimeErrorFuncName)) {
            responseObserver.onNext(InvokeResponse.newBuilder()
                .setCode(InvokeResponse.Code.RUNTIME_ERROR)
                .build());
          } else if (request.getName().equals(testRetryFuncName)) {
            responseObserver.onNext(InvokeResponse.newBuilder()
                .setCode(InvokeResponse.Code.RETRY)
                .build());
          } else {
            responseObserver.onNext(InvokeResponse.newBuilder()
                .setCode(InvokeResponse.Code.NO_SUCH_FUNCTION)
                .build());
          }
          responseObserver.onCompleted();
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
          if (request.getImage().equals(errorImage)) {
            responseObserver.onNext(InitFunctionResponse.newBuilder()
                .setCode(InitFunctionResponse.Code.ERROR)
                .build());
          } else if (request.getImage().equals(defaultImage)) {
            responseObserver.onNext(InitFunctionResponse.newBuilder()
                .setCode(InitFunctionResponse.Code.OK)
                .build());
          } else {
            responseObserver.onNext(InitFunctionResponse.newBuilder()
                .setCode(InitFunctionResponse.Code.UNRECOGNIZED)
                .build());
          }
          responseObserver.onCompleted();
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
    hasChanged = new HashMap<>();
    maintainerServer = new WorkerMaintainerServer(7777, hasChanged, new ReentrantLock());
    String serverName = InProcessServerBuilder.generateName();
    channel = ManagedChannelBuilder.forTarget("127.0.0.1:7777").usePlaintext().build();
    workerServer = InProcessServerBuilder
        .forName(serverName).directExecutor().addService(workerServerImpl).build().start();
    workerChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    backend = Executors.newFixedThreadPool(1);
    backend.execute(new Runnable() {
      @Override
      public void run() {
        try {
          maintainerServer.start();
          maintainerServer.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
          logger.fatal(e.getMessage());
        }
      }
    });
    Thread.sleep(2000); // wait for manager server start.
    ManagerBlockingStub client = ManagerGrpc.newBlockingStub(channel);
    ManagerOuterClass.RegisterResponse resp = client
        .register(ManagerOuterClass.RegisterRequest.newBuilder()
            .setAddr("127.0.0.1:" + String.valueOf(8080)) // unused port
            .setId(workerId)
            .build());
    if (!resp.getCode().equals(Code.OK)) {
      System.exit(-1);
    }
    // because of the worker server is in the process, it cannot use ip to connection.
    // we inject the channel instead.
    Worker worker = maintainerServer.getWorkers()
        .get(workerId);
    worker.setChannel(workerChannel);
    maintainerServer.getWorkers().put(workerId, worker);
  }

  @Test
  public void TestInitFunction() {
    testResource.setImage(defaultImage);
    this.maintainerServer.InitFunction(workerId, testResource);
    List<String> workerList = this.maintainerServer.getFunctionWorkerMap()
        .get(testFuncName);
    Assert.assertTrue(workerList.contains(workerId));
  }

  @Test
  public void TestInvokeFunction() {
    testResource.setImage(defaultImage);
    this.maintainerServer.InitFunction(workerId, testResource);
    List<String> workerList = this.maintainerServer.getFunctionWorkerMap()
        .get(testFuncName);
    Assert.assertTrue(workerList.contains(workerId));
    byte[] output = this.maintainerServer.invokeFunction(testResource, new byte[1]);
    Assert.assertNotNull(output);
  }

  @Test
  public void TestInvokeRuntimeErrorFunction() {
    testResource.setImage(defaultImage);
    testResource.setFuncName(testRuntimeErrorFuncName);
    this.maintainerServer.InitFunction(workerId, testResource);
    List<String> workerList = this.maintainerServer.getFunctionWorkerMap()
        .get(testFuncName);
    Assert.assertTrue(workerList.contains(workerId));
    byte[] output = this.maintainerServer.invokeFunction(testResource, new byte[1]);
    Assert.assertNull(output);
  }

  @Test
  public void TestInvokeNoSuchFunctionFunction() {
    testResource.setImage(defaultImage);
    testResource.setFuncName(testRuntimeErrorFuncName);
    this.maintainerServer.InitFunction(workerId, testResource);
    List<String> workerList = this.maintainerServer.getFunctionWorkerMap()
        .get(testFuncName);
    Assert.assertTrue(workerList.contains(workerId));
    byte[] output = this.maintainerServer.invokeFunction(testResource, new byte[1]);
    Assert.assertNull(output);
  }

  @After
  public void tearDown() throws InterruptedException {
    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
    workerChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
    backend.shutdown();
  }
}
