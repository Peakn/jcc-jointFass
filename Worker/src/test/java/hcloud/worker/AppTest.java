package hcloud.worker;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.fc.springcloud.docker.Client;
import com.fc.springcloud.docker.ContainerMaintainer;
import com.fc.springcloud.docker.WorkerServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jointfaas.manager.ManagerGrpc.ManagerImplBase;
import jointfaas.manager.ManagerOuterClass.RegisterRequest;
import jointfaas.manager.ManagerOuterClass.RegisterResponse;
import jointfaas.manager.ManagerOuterClass.SyncRequest;
import jointfaas.worker.InitFunctionRequest;
import jointfaas.worker.InitFunctionResponse;
import jointfaas.worker.InvokeRequest;
import jointfaas.worker.InvokeResponse;
import jointfaas.worker.InvokeResponse.Code;
import jointfaas.worker.WorkerGrpc;
import jointfaas.worker.WorkerGrpc.WorkerBlockingStub;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for Worker.
 */
public class AppTest {

  static private final String JAVACodeURI = "http://mesh:8081/index.jar";
  static private final String JAVAFuncName = "test-java";
  static private final String JAVAImage = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-java:v1.0";
  static private final String JAVARuntime = "Java8";
  static private final String PythonCodeURI = "http://mesh:8081/index-old.zip";
  static private final String PythonFuncName = "test-python";
  static private final String PythonImage = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-python:v1.0";
  static private final String PythonRuntime = "Python3";
  static private final String JSCodeURI = "http://mesh:8081/index-js.zip";
  static private final String JSFuncName = "test-js";
  static private final String JSImage = "registry.cn-shanghai.aliyuncs.com/jointfaas-serverless/env-javascript:v1.0";
  static private final String JSRuntime = "Node10";
  // in App Test, we should mock Manager
  @Rule
  public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  private ManagedChannel channel;
  private ManagedChannel managerChannel;
  private WorkerServer workerServer;
  private ManagedChannel workerChannel;
  private ManagedChannel loadCodeChannel;
  private Map<String, List<String>> functionInstances = new HashMap<>();
  private ManagerImplBase managerImpl = mock(ManagerImplBase.class, delegatesTo(
      new ManagerImplBase() {
        @Override
        public void register(RegisterRequest request,
            StreamObserver<RegisterResponse> responseObserver) {
          WorkerBlockingStub loadCodeClient = WorkerGrpc
              .newBlockingStub(loadCodeChannel);
          try {
            InitFunctionResponse JAVAResponse = loadCodeClient.initFunction(
                InitFunctionRequest.newBuilder()
                    .setMemorySize(100)
                    .setTimeout(1)
                    .setCodeURI(JAVACodeURI)
                    .setFuncName(JAVAFuncName)
                    .setImage(JAVAImage)
                    .setRuntime(JAVARuntime)
                    .build()
            );
            InitFunctionResponse PythonResponse = loadCodeClient.initFunction(
                InitFunctionRequest.newBuilder()
                    .setMemorySize(100)
                    .setTimeout(1)
                    .setCodeURI(PythonCodeURI)
                    .setFuncName(PythonFuncName)
                    .setImage(PythonImage)
                    .setRuntime(PythonRuntime)
                    .build()
            );
            InitFunctionResponse JSResponse = loadCodeClient.initFunction(
                InitFunctionRequest.newBuilder()
                    .setMemorySize(100)
                    .setTimeout(1)
                    .setCodeURI(JSCodeURI)
                    .setFuncName(JSFuncName)
                    .setImage(JSImage)
                    .setRuntime(JSRuntime)
                    .build()
            );
            RegisterResponse registerResp = null;
            if (JAVAResponse.getCode().equals(InitFunctionResponse.Code.OK) && PythonResponse
                .getCode().equals(InitFunctionResponse.Code.OK)) {
              registerResp = RegisterResponse.newBuilder()
                  .setCode(RegisterResponse.Code.OK)
                  .setMsg("register ok")
                  .build();
            } else {
              registerResp = RegisterResponse.newBuilder()
                  .setCode(RegisterResponse.Code.ERROR)
                  .setMsg(JAVAResponse.getMsg() + " " + PythonResponse.getMsg())
                  .build();
            }
            responseObserver.onNext(registerResp);
            responseObserver.onCompleted();
          } catch (StatusRuntimeException e) {
            System.out.println(e.getMessage());
          }
          try {
            loadCodeChannel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            System.out.println(e.getMessage());
          }
        }
        @Override
        public io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.SyncRequest> sync(
            io.grpc.stub.StreamObserver<jointfaas.manager.ManagerOuterClass.SyncResponse> responseObserver) {
          return new io.grpc.stub.StreamObserver<SyncRequest>() {

            @Override
            public void onNext(SyncRequest syncRequest) {
              System.out.println("get sync request:" + syncRequest.toString());
              functionInstances.put(syncRequest.getFunctionName(), syncRequest.getInstancesList());
            }

            @Override
            public void onError(Throwable throwable) {
              System.out.println("sync request:" + throwable.getMessage());
            }

            @Override
            public void onCompleted() {
              System.out.println("test on completed");
            }
          };
        }
      }
  ));


  @Before
  public void setUp() throws Exception {
    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    InProcessServerBuilder
        .forName(serverName).directExecutor().addService(managerImpl).build().start();

    // Create a client channel and register for automatic graceful shutdown.
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

    managerChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

    // start server
    String fileName = "./config_test.properties";
    InputStream is = null;
    Properties prop = new Properties();
    try {
      is = new FileInputStream(fileName);
    } catch (FileNotFoundException ex) {
      System.err.println(ex.getMessage());
      System.exit(-1);
    }
    try {
      prop.load(is);
    } catch (IOException ex) {
      System.err.println(ex.getMessage());
      System.exit(-1);
    }
    ExecutorService fixedThreadPool = Executors.newFixedThreadPool(1);
    Client client = new Client(DefaultDockerClient.fromEnv().build(), prop);
    ContainerMaintainer containerMaintainer = new ContainerMaintainer(client, managerChannel, prop.getProperty("id", "1"));
    workerServer = new WorkerServer(containerMaintainer, prop);
    fixedThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        try {
          workerServer.Start(channel);
          workerServer.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
          System.out.println(e.getMessage());
        }
      }
    });
    workerChannel = ManagedChannelBuilder
        .forTarget(prop.getProperty("addr", "127.0.0.1:8001"))
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
    loadCodeChannel = ManagedChannelBuilder
        .forTarget(prop.getProperty("addr", "127.0.0.1:8001"))
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
  }

  @After
  public void finalize() throws InterruptedException, DockerException, DockerCertificateException {
    DefaultDockerClient client = DefaultDockerClient.fromEnv().build();
    List<Container> containers = client
        .listContainers(ListContainersParam.withLabel("type", "jointfaas"));
//    for (Container container : containers) {
//      client.stopContainer(container.id(), 0);
//    }
    workerChannel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
    workerServer.stop();
    functionInstances = new HashMap<>();
  }


  @Test
  public void InvokeJAVATest() throws InterruptedException {
    // Invoke twice, the first time will return error because of no runtime is running
    // The second time you will get the true result
    Thread.sleep(3000);
    WorkerBlockingStub workerTestClient = WorkerGrpc.newBlockingStub(workerChannel);
    ByteString payload = ByteString.copyFrom("username".getBytes());
    InvokeResponse resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(JAVAFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(Code.RETRY, resp.getCode());
    Thread.sleep(3000);
    resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(JAVAFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(Code.OK, resp.getCode());
    Assert.assertEquals(functionInstances.get(JAVAFuncName).size(), 1);
  }

  @Test
  public void InvokeJSTest() throws InterruptedException {
    // Invoke twice, the first time will return error because of no runtime is running
    // The second time you will get the true result
    Thread.sleep(3000);
    WorkerBlockingStub workerTestClient = WorkerGrpc.newBlockingStub(workerChannel);
    ByteString payload = ByteString.copyFrom("{\"username\":\"A\"}".getBytes());
    InvokeResponse resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(JSFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(Code.RETRY, resp.getCode());
    Thread.sleep(3000);
    resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(JSFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(Code.OK, resp.getCode());
//    Assert.assertEquals(functionInstances.get(JSFuncName).size(), 1);
  }


  @Test
  public void InvokePythonTest() throws InterruptedException {
    // Invoke twice, the first time will return error because of no runtime is running
    // The second time you will get the true result
    Thread.sleep(3000);
    WorkerBlockingStub workerTestClient = WorkerGrpc.newBlockingStub(workerChannel);
    ByteString payload = ByteString.copyFrom("username".getBytes());
    InvokeResponse resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(PythonFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(Code.RETRY, resp.getCode());
    Thread.sleep(3000);
    resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(PythonFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(Code.OK, resp.getCode());
    Assert.assertEquals(functionInstances.get(PythonFuncName).size(), 1);
  }

  @Test
  public void InvokeErrorTest() throws InterruptedException {
    // Invoke twice, the first time will return error because of no runtime is running
    // The second time you will get the true result
    WorkerBlockingStub workerTestClient = WorkerGrpc.newBlockingStub(workerChannel);
    ByteString payload = ByteString.copyFrom("username".getBytes());
    String ErrorFuncName = "error";
    InvokeResponse resp = workerTestClient.invoke(InvokeRequest.newBuilder()
        .setName(ErrorFuncName)
        .setPayload(payload)
        .build());
    Assert.assertEquals(resp.getCode(), Code.NO_SUCH_FUNCTION);
  }


}
