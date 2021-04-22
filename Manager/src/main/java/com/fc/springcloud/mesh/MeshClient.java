package com.fc.springcloud.mesh;

import com.alibaba.fastjson.JSONObject;
import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.mesh.exception.NotImplementedException;
import com.fc.springcloud.pojo.dto.ApplicationDto.StepDto;
import com.fc.springcloud.pojo.dto.PolicyDto;
import com.fc.springcloud.util.ZipUtil;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import jointfaas.mesh.definition.Definition.ApplicationSpec;
import jointfaas.mesh.definition.Definition.CreateApplicationRequest;
import jointfaas.mesh.definition.Definition.CreateApplicationResponse;
import jointfaas.mesh.definition.Definition.CreateFunctionRequest;
import jointfaas.mesh.definition.Definition.CreateFunctionResponse;
import jointfaas.mesh.definition.Definition.CreatePolicyRequest;
import jointfaas.mesh.definition.Definition.CreatePolicyResponse;
import jointfaas.mesh.definition.Definition.DeleteApplicationRequest;
import jointfaas.mesh.definition.Definition.DeleteApplicationResponse;
import jointfaas.mesh.definition.Definition.DeleteFunctionRequest;
import jointfaas.mesh.definition.Definition.DeletePolicyRequest;
import jointfaas.mesh.definition.Definition.DeletePolicyResponse;
import jointfaas.mesh.definition.Definition.FunctionSpec;
import jointfaas.mesh.definition.Definition.GetApplicationRequest;
import jointfaas.mesh.definition.Definition.GetApplicationResponse;
import jointfaas.mesh.definition.Definition.GetPolicyRequest;
import jointfaas.mesh.definition.Definition.GetPolicyResponse;
import jointfaas.mesh.definition.Definition.StatusCode;
import jointfaas.mesh.definition.Definition.UpdateFunctionRequest;
import jointfaas.mesh.definition.Definition.UpdateFunctionResponse;
import jointfaas.mesh.definition.Definition.UpdatePolicyRequest;
import jointfaas.mesh.definition.Definition.UpdatePolicyResponse;
import jointfaas.mesh.definition.DefinitionServerGrpc;
import jointfaas.mesh.definition.DefinitionServerGrpc.DefinitionServerBlockingStub;
import jointfaas.mesh.definition.DefinitionServerGrpc.DefinitionServerStub;
import jointfaas.mesh.model.Model.Application;
import jointfaas.mesh.model.Model.Info;
import jointfaas.mesh.model.Model.Method;
import jointfaas.mesh.model.Model.Step;
import lombok.Setter;
import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Setter
public class MeshClient {

  private static final Log logger = LogFactory.getLog(MeshClient.class);

  private final ExecutorService functionInfoUpdate;

  private Map<String, Map<String, UpdateFunctionClient>> ufcCollection;

  private Lock lock = new ReentrantLock();

  @Value("${mesh.target}")
  String target;

  @Value("#{'${mesh.definition}'.split(',')}")
  List<String> definition;

  @Value("${mesh.trace.host}")
  String traceHost;

  @Value("${mesh.trace.port}")
  Integer tracePort;

  public MeshClient() {
    ufcCollection = new HashMap<>();
    functionInfoUpdate = Executors.newCachedThreadPool();
  }

  @Setter
  static class UpdateFunctionClient implements StreamObserver<UpdateFunctionResponse> {

    private static final Log logger = LogFactory.getLog(UpdateFunctionClient.class);
    private Boolean stop;
    private String functionName;
    private String internalUrl;
    private String url;
    private Float price;
    private Cluster cluster;
    private ManagedChannel channel;
    private BlockingQueue<Float> priceUpstream;
    private BlockingQueue<Cluster> clusterUpstream;
    private StreamObserver<UpdateFunctionRequest> updateFunctionRequestObserver;
    private ExecutorService collectionThreads;
    private String provider;

    public UpdateFunctionClient(String functionName, String internalUrl, String url,
        BlockingQueue<Float> priceUpstream, BlockingQueue<Cluster> clusterUpstream,
        String provider) {
      this.functionName = functionName;
      this.url = url;
      this.internalUrl = internalUrl;
      this.priceUpstream = priceUpstream;
      this.clusterUpstream = clusterUpstream;
      this.stop = false;
      this.cluster = new Cluster(new ArrayList<>(), provider, functionName);
      this.price = 0.0f;
      this.collectionThreads = Executors.newFixedThreadPool(3);
      this.provider = provider;
    }

    public void start() {
      // start info collection thread
      logger.info("start info collection thread to sync");
      if (priceUpstream != null) {
        logger.info("price upstream start");
        // start price collection
        collectionThreads.execute(new Runnable() {
          @SneakyThrows
          @Override
          public void run() {
            do {
              price = priceUpstream.take();
            } while (!stop);
          }
        });
      }

      if (clusterUpstream != null) {
        logger.info("cluster upstream start");
        // start
        collectionThreads.execute(new Runnable() {
          @SneakyThrows
          @Override
          public void run() {
            do {
              cluster = clusterUpstream.take();
              logger.info("cluster get from upstream: " + cluster.toString());
            } while (!stop);
          }
        });
      }
      // todo add cron job to sync information to mesh center
      if (priceUpstream == null && clusterUpstream == null) {
        return;
      }

      collectionThreads.execute(new Runnable() {
        @SneakyThrows
        @Override
        public void run() {
          String hash = "";
          Float lastPrice = null;
          Cluster lastCluster = null;
          while (true) {
            if (hash.equals(cluster.toString()) && price.equals(lastPrice)) {
              Thread.sleep(100);
              continue;
            }
            hash = cluster.toString();
            lastPrice = price;
            lastCluster = new Cluster(cluster);
            logger.info("update cluster:" + cluster.toString());
            logger.info("update price:" + lastPrice);
            UpdateFunctionRequest req = UpdateFunctionRequest.newBuilder()
                .setFunctionSpec(FunctionSpec.newBuilder()
                    .setName(functionName).
                        setInfo(Info.newBuilder()
                            .setInternalUrl(internalUrl)
                            .setPrice(lastPrice)
                            .setUrl(url)
                            .addAllInstances(lastCluster.instances)
                            .build())
                    .setProvider(provider)
                    .build())
                .build();
            updateFunctionRequestObserver.onNext(req);
            Thread.sleep(100);
          }
        }
      });
    }


    @SneakyThrows
    @Override
    public void onNext(UpdateFunctionResponse updateFunctionResponse) {
    }

    @Override
    public void onError(Throwable throwable) {
      logger.fatal(throwable);
      this.onCompleted();
      this.stop = true;
    }

    @Override
    public void onCompleted() {
    }
  }

  public String chooseDefinition() {
    logger.info(definition);
    return definition.get((int)(Math.random() * definition.size()));
  }

  public void syncFunctionInfo(String functionName, String internalUrl, String url,
      BlockingQueue<Float> priceUpstream, BlockingQueue<Cluster> clusterUpstream,
      String provider) {

    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerStub client = DefinitionServerGrpc.newStub(channel);
    UpdateFunctionClient ufc = new UpdateFunctionClient(functionName, internalUrl, url,
        priceUpstream, clusterUpstream, provider);
    StreamObserver<UpdateFunctionRequest> updateFunctionRequestStreamObserver = client
        .updateFunction(ufc);
    ufc.setChannel(channel);
    ufc.setUpdateFunctionRequestObserver(updateFunctionRequestStreamObserver);
    lock.lock();
    Map<String, UpdateFunctionClient> providerCollection = ufcCollection
        .computeIfAbsent(provider, k -> new HashMap<>());
    providerCollection.put(functionName, ufc);
    lock.unlock();
    ufc.start();
  }

  public String injectInitializer() {
    return "index_mesh.mesh_initializer";
  }

  public String injectHandler() {
    return "index_mesh.handler";
  }

  public byte[] injectMesh(String functionName, RunEnvEnum runtime, String codeURL, String provider)
      throws IOException {
    logger.info(codeURL);
    URL resourceURL = new URL(codeURL);
    File resourceCode = File.createTempFile(functionName + runtime, "");
    logger.info("inject mesh step 1");
    FileUtils.copyURLToFile(resourceURL, resourceCode, 4000, 40000);
    logger.info("inject mesh step 2");
    String envCodeURI = null;
    // todo try to use local file first
    // todo configurable envCodeURI
    switch (runtime) {
      case python3: {
        File envPython = new File("./env-py.zip");
        if (envPython.exists()) {
          envCodeURI = envPython.toURI().toURL().toString();
        } else {
          envCodeURI = "http://mesh:8081/env-py.zip";
        }
        break;
      }
      case nodejs10: {
        File envJS = new File("./env-js.zip"); // code from aliyun-javascript-mesh-wrapper
        if (envJS.exists()) {
          envCodeURI = envJS.toURI().toURL().toString();
        } else {
          envCodeURI = "http://mesh:8081/env-js.zip";
        }
        break;
      }
      default: {
        throw new NotImplementedException("the runtime is unimplemented");
      }
    }
    File result = injectRuntime(resourceCode, new URL(envCodeURI));
    logger.info("inject mesh step 3");
    result = injectConfig(result, provider);
    logger.info("inject mesh step 4");
    return Files.readAllBytes(Paths.get(result.getPath()));
  }

  private String getApplicationNameByFunctionName(String functionName) {
    return "";
  }

  private File injectRuntime(File resourceCode, URL envURL) throws IOException {
    try {
      File envFile = File.createTempFile("python-env", ".zip");
      FileUtils.copyURLToFile(envURL, envFile);
      File result = ZipUtil.Mergev2(resourceCode, envFile);
      return result;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private File injectConfig(File resourceCode, String applicationName) throws IOException {
    assert resourceCode != null;
    JSONObject config = new JSONObject();
    JSONObject info = new JSONObject();
    JSONObject trace = new JSONObject();
    info.put("target", target);
    config.put("info", info);
    JSONObject traceConfig = new JSONObject();
    traceConfig.put("serviceName", applicationName);
    JSONObject traceReporter = new JSONObject();
    traceReporter.put("logSpans", true);
    traceReporter.put("agentHost", traceHost);
    traceReporter.put("agentPort", tracePort);
    traceConfig.put("reporter", traceReporter);
    JSONObject traceSampler = new JSONObject();
    traceSampler.put("type", "const");
    traceSampler.put("param", 1.0f);
    traceConfig.put("sampler", traceSampler);
    trace.put("config", traceConfig);
    config.put("trace", trace);
    String configString = config.toJSONString();

    ZipFile wrapper = new ZipFile(resourceCode);
    File configFile = File.createTempFile(applicationName + "config", ".json");
    BufferedWriter writer = Files.newBufferedWriter(configFile.toPath());
    logger.info("write config:" + configString);
    writer.write(configString);
    writer.flush();
    writer.close();
    ZipParameters parameters = new ZipParameters();
    parameters.setFileNameInZip("config.json");
    wrapper.addFile(configFile, parameters);
    return resourceCode;
  }

  public void injectEnv(Map<String, String> env, String provider, String functionName) {
    env.put("PROVIDER", provider);
    env.put("FUNC_NAME", functionName);
    env.put("MESH", target);
  }

  public void createApplication(String applicationName, String entryStep,
      Map<String, StepDto> rawSteps) {
    Map<String, Step> steps = new HashMap<>();
    for (StepDto s : rawSteps.values()) {
      steps.put(s.getStepName(), s.ToStep());
    }
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    CreateApplicationResponse resp = client
        .createApplication(CreateApplicationRequest.newBuilder()
            .setApplicationSpec(ApplicationSpec.newBuilder()
                .setApplication(Application.newBuilder()
                    .setName(applicationName)
                    .setEntryStep(entryStep)
                    .putAllSteps(steps)
                    .build())
                .build())
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getMsg());
    }
    channel.shutdown();
  }

  public Application getApplication(String applicationName) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    GetApplicationResponse resp = client
        .getApplication(GetApplicationRequest.newBuilder()
            .setName(applicationName)
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getMsg());
    }
    channel.shutdown();
    return resp.getApplicationSpec().getApplication();
  }


  public void deleteApplication(String applicationName) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    DeleteApplicationResponse resp = client.deleteApplication(DeleteApplicationRequest.newBuilder()
        .setName(applicationName)
        .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getMsg());
    }
    channel.shutdown();
  }

  public void createPolicy(PolicyDto policyDto) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    CreatePolicyResponse resp = client
        .createPolicy(CreatePolicyRequest.newBuilder()
            .setPolicy(policyDto.ToPolicy())
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getStatusCode().toString());
    }
    channel.shutdown();
  }

  public void deletePolicy(String name) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    DeletePolicyResponse resp = client
        .deletePolicy(DeletePolicyRequest.newBuilder()
            .setName(name)
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getStatusCode().toString());
    }
    channel.shutdown();
  }

  public void updatePolicy(PolicyDto policyDto) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    UpdatePolicyResponse resp = client
        .updatePolicy(UpdatePolicyRequest.newBuilder()
            .setPolicy(policyDto.ToPolicy())
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getStatusCode().toString());
    }
    channel.shutdown();
  }

  public PolicyDto getPolicy(String name) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    GetPolicyResponse resp = client
        .getPolicy(GetPolicyRequest.newBuilder()
            .setName(name)
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getStatusCode().toString());
    }
    channel.shutdown();
    return new PolicyDto(resp.getPolicy());
  }

//  public void updateApplication(String applicationName, List<String> rawSteps) {
//    List<Model.Step> steps = new ArrayList<>();
//    for (String step : rawSteps) {
//      steps.add(Step.newBuilder().setFunctionName(step).build());
//    }
//    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
//        .build();
//    DefinitionServerBlockingStub client = DefinitionServerGrpc
//        .newBlockingStub(channel);
//    UpdateApplicationResponse resp = client
//        .updateApplication(UpdateApplicationRequest.newBuilder()
//            .setApplicationSpec(ApplicationSpec.newBuilder()
//                .setApplication(Application.newBuilder()
//                    .setName(applicationName)
//                    .addAllStepChains(steps)
//                    .build())
//                .build())
//            .build());
//    if (!resp.getStatusCode().equals(StatusCode.OK)) {
//      channel.shutdown();
//      throw new RuntimeException(resp.getMsg());
//    }
//    channel.shutdown();
//  }

  public void createFunctionInMesh(String name, String method) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    CreateFunctionResponse resp = client
        .createFunction(CreateFunctionRequest.newBuilder()
            .setName(name)
            .setMethod(Method.valueOf(method))
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getMsg());
    }
    channel.shutdown();
  }

  public void deleteFunctionInMesh(String name) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(chooseDefinition()).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    client.deleteFunction(DeleteFunctionRequest.newBuilder()
        .setName(name)
        .build());
    channel.shutdown();
  }


  // XDS will connection to mesh control plane and sync information storing in memory.
  // this function should call in thread at start time (only once)
  public void XDS() {
  }


}
