package com.fc.springcloud.mesh;

import com.alibaba.fastjson.JSONObject;
import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.mesh.exception.NotImplementedException;
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
import jointfaas.mesh.definition.Definition.ApplicationSpec;
import jointfaas.mesh.definition.Definition.CreateApplicationRequest;
import jointfaas.mesh.definition.Definition.CreateApplicationResponse;
import jointfaas.mesh.definition.Definition.CreateFunctionRequest;
import jointfaas.mesh.definition.Definition.CreateFunctionResponse;
import jointfaas.mesh.definition.Definition.DeleteApplicationRequest;
import jointfaas.mesh.definition.Definition.DeleteApplicationResponse;
import jointfaas.mesh.definition.Definition.DeleteFunctionRequest;
import jointfaas.mesh.definition.Definition.FunctionSpec;
import jointfaas.mesh.definition.Definition.StatusCode;
import jointfaas.mesh.definition.Definition.UpdateApplicationRequest;
import jointfaas.mesh.definition.Definition.UpdateApplicationResponse;
import jointfaas.mesh.definition.Definition.UpdateFunctionRequest;
import jointfaas.mesh.definition.Definition.UpdateFunctionResponse;
import jointfaas.mesh.definition.DefinitionServerGrpc;
import jointfaas.mesh.definition.DefinitionServerGrpc.DefinitionServerBlockingStub;
import jointfaas.mesh.definition.DefinitionServerGrpc.DefinitionServerStub;
import jointfaas.mesh.model.Model;
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

  private Map<String, UpdateFunctionClient> ufcCollection;

  @Value("${mesh.target}")
  String target;

  @Value("${mesh.definition}")
  String definition;

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

    public UpdateFunctionClient(String functionName, String internalUrl, String url,
        BlockingQueue<Float> priceUpstream, BlockingQueue<Cluster> clusterUpstream) {
      this.functionName = functionName;
      this.url = url;
      this.internalUrl = internalUrl;
      this.priceUpstream = priceUpstream;
      this.clusterUpstream = clusterUpstream;
      this.stop = false;
      this.price = 0.0f;
      this.collectionThreads = Executors.newFixedThreadPool(3);
    }

    public void start() {
      // start info collection thread
      logger.info("start info collection thread to sync");
      if (priceUpstream != null) {
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
      collectionThreads.execute(new Runnable() {
        @SneakyThrows
        @Override
        public void run() {
          String hash = "";
          while (true) {
            logger.info(cluster);
            if (cluster != null) {
              if (hash.equals(cluster.toString())) {
                logger.info("cluster: " + cluster.toString());
                Thread.sleep(1000);
                continue;
              }
              hash = cluster.toString();
              logger.info("update cluster" + cluster.toString());
              UpdateFunctionRequest req = UpdateFunctionRequest.newBuilder()
                  .setFunctionSpec(FunctionSpec.newBuilder()
                      .setName(functionName).
                          setInfo(Info.newBuilder()
                              .setInternalUrl(internalUrl)
                              .setPrice(price)
                              .setUrl(url)
                              .addAllInstances(cluster.instances)
                              .build())
                      .setProvider(cluster.provider)
                      .build())
                  .build();
              updateFunctionRequestObserver.onNext(req);
            }
            Thread.sleep(1000);
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

  public void syncFunctionInfo(String functionName, String internalUrl, String url,
      BlockingQueue<Float> priceUpstream, BlockingQueue<Cluster> clusterUpstream) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
        .build();
    DefinitionServerStub client = DefinitionServerGrpc.newStub(channel);
    UpdateFunctionClient ufc = new UpdateFunctionClient(functionName, internalUrl, url,
        priceUpstream, clusterUpstream);
    StreamObserver<UpdateFunctionRequest> updateFunctionRequestStreamObserver = client
        .updateFunction(ufc);
    ufc.setChannel(channel);
    ufc.setUpdateFunctionRequestObserver(updateFunctionRequestStreamObserver);
    ufcCollection.put(functionName, ufc);
    ufc.start();
  }

  public String injectInitializer() {
    return "index.mesh_initializer";
  }

  public String injectHandler() {
    return "index.handler";
  }

  public byte[] injectMesh(String functionName, RunEnvEnum runtime, String codeURL)
      throws IOException {
    URL resourceURL = new URL(codeURL);
    File resourceCode = File.createTempFile(functionName + runtime, "");
    FileUtils.copyURLToFile(resourceURL, resourceCode);
    String envCodeURI = null;
    // todo configurable envCodeURI
    switch (runtime) {
      case python3: {
        envCodeURI = "http://106.15.225.249:8080/env.zip";
        break;
      }
      default: {
        throw new NotImplementedException("the runtime is unimplemented");
      }
    }
    File result = injectRuntime(resourceCode, new URL(envCodeURI));
    String applicationName = getApplicationNameByFunctionName(functionName);
    result = injectConfig(result, applicationName);
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
    env.put("POLICY", "simple"); // todo hard code
  }

  public void createApplication(String applicationName, List<String> rawSteps) {
    List<Model.Step> steps = new ArrayList<>();
    for (String step : rawSteps) {
      steps.add(Step.newBuilder().setFunctionName(step).build());
    }
    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    CreateApplicationResponse resp = client
        .createApplication(CreateApplicationRequest.newBuilder()
            .setApplicationSpec(ApplicationSpec.newBuilder()
                .setApplication(Application.newBuilder()
                    .setName(applicationName)
                    .addAllStepChains(steps)
                    .build())
                .build())
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getMsg());
    }
    channel.shutdown();
  }

  public void deleteApplication(String applicationName) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
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

  public void updateApplication(String applicationName, List<String> rawSteps) {
    List<Model.Step> steps = new ArrayList<>();
    for (String step : rawSteps) {
      steps.add(Step.newBuilder().setFunctionName(step).build());
    }
    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    UpdateApplicationResponse resp = client
        .updateApplication(UpdateApplicationRequest.newBuilder()
            .setApplicationSpec(ApplicationSpec.newBuilder()
                .setApplication(Application.newBuilder()
                    .setName(applicationName)
                    .addAllStepChains(steps)
                    .build())
                .build())
            .build());
    if (!resp.getStatusCode().equals(StatusCode.OK)) {
      channel.shutdown();
      throw new RuntimeException(resp.getMsg());
    }
    channel.shutdown();
  }

  public void createFunctionInMesh(String name, String method) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
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
    ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
        .build();
    DefinitionServerBlockingStub client = DefinitionServerGrpc
        .newBlockingStub(channel);
    client.deleteFunction(DeleteFunctionRequest.newBuilder()
        .setName(name)
        .build());
    channel.shutdown();
  }
}
