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
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jointfaas.mesh.definition.Definition.CreateFunctionRequest;
import jointfaas.mesh.definition.Definition.CreateFunctionResponse;
import jointfaas.mesh.definition.Definition.DeleteFunctionRequest;
import jointfaas.mesh.definition.Definition.FunctionSpec;
import jointfaas.mesh.definition.Definition.StatusCode;
import jointfaas.mesh.definition.Definition.UpdateFunctionRequest;
import jointfaas.mesh.definition.Definition.UpdateFunctionResponse;
import jointfaas.mesh.definition.DefinitionServerGrpc;
import jointfaas.mesh.definition.DefinitionServerGrpc.DefinitionServerBlockingStub;
import jointfaas.mesh.definition.DefinitionServerGrpc.DefinitionServerStub;
import jointfaas.mesh.model.Model.Info;
import jointfaas.mesh.model.Model.Method;
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

  @Value("${mesh.target}")
  String target;

  @Value("${mesh.definition}")
  String definition;

  @Value("${mesh.trace.host}")
  String traceHost;

  @Value("${mesh.trace.port}")
  Integer tracePort;

  public MeshClient() {
    functionInfoUpdate = Executors.newCachedThreadPool();
  }

  @Setter
  static class UpdateFunctionClient implements StreamObserver<UpdateFunctionResponse> {

    private Boolean stop;
    private String functionName;
    private String internalUrl;
    private String url;
    private Float price;
    private Cluster cluster;
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
        // start
        collectionThreads.execute(new Runnable() {
          @SneakyThrows
          @Override
          public void run() {
            do {
              cluster = clusterUpstream.take();
            } while (!stop);
          }
        });
      }
      // todo add cron job to sync information to mesh center
      collectionThreads.execute(new Runnable() {
        @SneakyThrows
        @Override
        public void run() {
          while (true) {
            if (cluster != null) {
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
    functionInfoUpdate.execute(new Runnable() {
      @Override
      public void run() {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(definition).usePlaintext()
            .build();
        DefinitionServerStub client = DefinitionServerGrpc.newStub(channel);
        UpdateFunctionClient ufc = new UpdateFunctionClient(functionName, internalUrl, url,
            priceUpstream, clusterUpstream);
        StreamObserver<UpdateFunctionRequest> updateFunctionRequestStreamObserver = client
            .updateFunction(ufc);
        ufc.setUpdateFunctionRequestObserver(updateFunctionRequestStreamObserver);
        ufc.start();
      }
    });
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
