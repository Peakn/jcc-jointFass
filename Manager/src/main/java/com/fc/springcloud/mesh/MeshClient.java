package com.fc.springcloud.mesh;

import com.alibaba.fastjson.JSONObject;
import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.mesh.exception.NotImplementedException;
import com.fc.springcloud.util.ZipUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MeshClient {

  private static final Log logger = LogFactory.getLog(MeshClient.class);

  @Value("${mesh.target}")
  String target;

  @Value("${mesh.trace.host}")
  String traceHost;

  @Value("${mesh.trace.port}")
  Integer tracePort;


  public MeshClient() {
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
}
