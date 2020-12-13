package com.fc.springcloud.provider.Impl.alicloud;

import cn.hutool.core.io.FileUtil;
import com.aliyuncs.fc.client.FunctionComputeClient;
import com.aliyuncs.fc.exceptions.ClientException;
import com.aliyuncs.fc.model.Code;
import com.aliyuncs.fc.model.HttpAuthType;
import com.aliyuncs.fc.model.HttpMethod;
import com.aliyuncs.fc.model.HttpTriggerConfig;
import com.aliyuncs.fc.request.CreateFunctionRequest;
import com.aliyuncs.fc.request.CreateServiceRequest;
import com.aliyuncs.fc.request.CreateTriggerRequest;
import com.aliyuncs.fc.request.DeleteFunctionRequest;
import com.aliyuncs.fc.request.InvokeFunctionRequest;
import com.aliyuncs.fc.request.ListFunctionsRequest;
import com.aliyuncs.fc.request.UpdateFunctionRequest;
import com.aliyuncs.fc.response.CreateFunctionResponse;
import com.aliyuncs.fc.response.CreateServiceResponse;
import com.aliyuncs.fc.response.CreateTriggerResponse;
import com.aliyuncs.fc.response.DeleteFunctionResponse;
import com.aliyuncs.fc.response.InvokeFunctionResponse;
import com.aliyuncs.fc.response.ListFunctionsResponse;
import com.aliyuncs.fc.response.UpdateFunctionResponse;
import com.fc.springcloud.config.AliyunConfig;
import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.provider.Impl.alicloud.exception.CreateTriggerException;
import com.fc.springcloud.provider.PlatformProvider;
import com.fc.springcloud.util.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class AliCloudProvider implements PlatformProvider {

  private static final Log logger = LogFactory.getLog(AliCloudProvider.class);

  private static final String SERVICE_NAME = "demo";

  private static final String provider = "alicloud";

  @Autowired
  AliyunConfig config;

  @Autowired
  private FunctionComputeClient fcClient;

  @Autowired
  private String role;

  @Value("${mesh.use}")
  private boolean enableInject;

  @Autowired
  private MeshClient meshInjector;


  public void CreateTrigger(String functionName) {
    CreateTriggerRequest ctRequest = new CreateTriggerRequest(SERVICE_NAME, functionName);
    ctRequest.setTriggerType("http");
    ctRequest.setTriggerName("http");
    HttpTriggerConfig httpTriggerConfig = new HttpTriggerConfig(HttpAuthType.ANONYMOUS,
        new HttpMethod[]{HttpMethod.GET});
    ctRequest.setTriggerConfig(httpTriggerConfig);
    ctRequest.setQualifier("LATEST");
    CreateTriggerResponse resp = fcClient.createTrigger(ctRequest);
    if (resp.getStatus() != HttpStatus.OK.value()) {
      throw new CreateTriggerException(new String(resp.getContent()));
    }
  }
  //Create Service
  public void CreateService(String serviceName) {
    //Create Service
    CreateServiceRequest csReq = new CreateServiceRequest();
    csReq.setServiceName(serviceName);
    csReq.setDescription("The service aims at jointFaas.");
    try {
      CreateServiceResponse csResp = fcClient.createService(csReq);
      logger.info("Created service, request ID " + csResp.getRequestId());
    } catch (Exception e) {
      logger.warn("alicloud create service error: " + e.getMessage());
      throw e;
    }
  }

  private String parseInternalUrl(String serviceName,  String functionName) {
    return "";
  }

  private String parseUrl(String serviceName,  String functionName) {
    return "";
  }

  private byte[] prepareCodeZip(String codeURL, String runtime) throws IOException {
    File[] addFiles = new File[1];

    File tmpFile = File.createTempFile("tmpFunctionFile", ".zip");
    FileUtils.copyURLToFile(new URL(codeURL), tmpFile);
    String toFilePath = null;
    if ("python3".equals(runtime)) {
      //append alicloud specific entrypoint into the zipfile
      ClassPathResource resource = new ClassPathResource("static/aliCloud/python3/jointfaas.py");
      toFilePath = "jointfaas.py";
      File inuModel = new File(toFilePath);
      FileUtils.copyToFile(resource.getInputStream(), inuModel);
      addFiles[0] = inuModel;
    }
    ZipUtil.addFilesToExistingZip(tmpFile, addFiles);
    FileUtil.del(toFilePath);
    return Files.readAllBytes(Paths.get(tmpFile.getPath()));
  }

  @Override
  public void CreateFunction(String functionName, String codeURL, String runTimeEnvir)
      throws IOException {
    // Create a function
    CreateFunctionRequest cfReq = new CreateFunctionRequest(SERVICE_NAME);
    cfReq.setFunctionName(functionName);
    cfReq.setMemorySize(128);
    cfReq.setRuntime(runTimeEnvir);

    if (enableInject) {
      cfReq.setInitializer(meshInjector.injectInitializer());
      cfReq.setHandler(meshInjector.injectHandler());
      Map<String, String> env = new HashMap<>();
      meshInjector.injectEnv(env, provider, functionName);
      cfReq.setEnvironmentVariables(env);
      byte[] zipCode = meshInjector
          .injectMesh(functionName, RunEnvEnum.valueOf(runTimeEnvir), codeURL);
      Code code = new Code().setZipFile(zipCode);
      cfReq.setCode(code);
    } else {
      cfReq.setHandler("jointfaas.handler");
      byte[] zipCode = prepareCodeZip(codeURL, runTimeEnvir);
      Code code = new Code().setZipFile(zipCode);
      cfReq.setCode(code);
    }

    try {
      CreateFunctionResponse cfResp = fcClient.createFunction(cfReq);
      CreateTrigger(functionName);
      logger.info("Created function, request ID " + cfResp.getRequestId());
      logger.info("Create function at time: " + cfResp.getCreatedTime());
    } catch (ClientException e) {
      if (e.getErrorCode().equals("ServiceNotFound")) {
        this.CreateService(SERVICE_NAME);
        logger.info("The Service not exist. We have recreated service and function");
        CreateFunctionResponse cfResp = fcClient.createFunction(cfReq);
        CreateTrigger(functionName);
      } else if (e.getErrorCode().equals("FunctionAlreadyExists")) {
        this.UpdateFunction(functionName, codeURL, runTimeEnvir);
        logger.info("The function has existed, and has been updated.");
      } else {
        logger.info(
            "The function create fail. message: " + e.getMessage() + "\n" + e.getErrorMessage()
                + "\n" + e.getErrorCode());
      }
    }
    // todo set the priceUpstream
    meshInjector.syncFunctionInfo(functionName, parseInternalUrl(functionName, SERVICE_NAME), parseUrl(functionName, SERVICE_NAME), null, null);
  }

  @Override
  public String InvokeFunction(String functionName, String jsonString) {

    InvokeFunctionRequest invkReq = new InvokeFunctionRequest(SERVICE_NAME, functionName);
    //设置参数
//        String payload = jsonObject.toJSONString();
    invkReq.setPayload(jsonString.getBytes());

    InvokeFunctionResponse invkResp = fcClient.invokeFunction(invkReq);
    logger.info("Function invoke success, requestedId: " + invkResp.getRequestId());
    logger.info("Run result：" + new String(invkResp.getContent()));
    String result =
        "Function invoke success, requestedId: " + invkResp.getRequestId() + ".Run result："
            + new String(invkResp.getContent());
    logger.info(result);
    return new String(invkResp.getContent());
  }

  @Override
  public void UpdateFunction(String functionName, String codeURL, String runTimeEnvir)
      throws IOException {
    UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
    ufReq.setDescription("Update Function");

    ufReq.setRuntime(runTimeEnvir);
    ufReq.setHandler("jointfaas.handler");
    //更新代码
    if (enableInject) {
      ufReq.setInitializer(meshInjector.injectInitializer());
      ufReq.setHandler(meshInjector.injectHandler());
      Map<String, String> env = new HashMap<>();
      meshInjector.injectEnv(env, provider, functionName);
      ufReq.setEnvironmentVariables(env);
      byte[] zipCode = meshInjector
          .injectMesh(functionName, RunEnvEnum.valueOf(runTimeEnvir), codeURL);
      Code code = new Code().setZipFile(zipCode);
      ufReq.setCode(code);
    } else {
      byte[] zipCode = prepareCodeZip(codeURL, runTimeEnvir);
      assert zipCode != null;
      Code code = new Code().setZipFile(zipCode);
      ufReq.setCode(code);
    }

    try {
      UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
      logger.info("Update function configurations and code success, the request id:" + ufResp
          .getFunctionId());
    } catch (ClientException e) {
      if (e.getErrorCode().equals("FunctionNotFound")) {
        logger.info(
            "Function updateFail.This function not exists, please check functionName or create.");
      } else {
        logger.info("Function update fail." + e.getErrorCode());
      }
      throw e;
    }
  }

  @Override
  public void DeleteFunction(String functionName) {
    DeleteFunctionRequest dfRep = new DeleteFunctionRequest(SERVICE_NAME, functionName);
    try {
      DeleteFunctionResponse dfResp = fcClient.deleteFunction(dfRep);
      logger.info("Function Delete Success.Delete function success, the requested id: " + dfResp
          .getRequestId());
    } catch (ClientException e) {
      if (e.getErrorCode().equals("FunctionNotFound")) {
        logger.info("Function Delete Fail.This function not exist, please check functionName");
      } else {
        e.printStackTrace();
      }
      throw e;
    }
  }

  @Override
  public Object ListFunction() {
    ListFunctionsRequest lfReq = new ListFunctionsRequest(SERVICE_NAME);
    ListFunctionsResponse lfResp = fcClient.listFunctions(lfReq);
    return lfResp;
  }
}