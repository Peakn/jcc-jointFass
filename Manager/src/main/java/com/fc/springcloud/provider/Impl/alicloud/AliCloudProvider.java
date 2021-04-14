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
import com.fc.springcloud.mapping.FunctionMapper;
import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.query.FunctionQuery;
import com.fc.springcloud.provider.Impl.alicloud.exception.CreateTriggerException;
import com.fc.springcloud.provider.PlatformProvider;
import com.fc.springcloud.util.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.io.FileUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AliCloudProvider implements PlatformProvider {

  private static final Log logger = LogFactory.getLog(AliCloudProvider.class);
  
  private static final String provider = "alicloud";

  @Autowired
  AliyunConfig config;

  @Autowired
  private String role;

  @Value("${mesh.use}")
  private boolean enableInject;

  @Autowired
  private MeshClient meshInjector;

  @Autowired
  private FunctionMapper functionMapper;

  private Map<String, BlockingQueue<Float>> priceSyncCollection;

  private ReadWriteLock lock;

  public AliCloudProvider() {
    priceSyncCollection = new HashMap<>();
    lock = new ReentrantReadWriteLock();
  }

  public void Start() {
    logger.info("alicloud start !!!!!!!!!!!!");
    List<FunctionDo> functions = this.functionMapper.listFunctionByPages(new FunctionQuery());
    for (FunctionDo func : functions) {
      this.SyncFunction(func.getFunctionName());
    }
  }

  public void CreateTrigger(String functionName) {
    CreateTriggerRequest ctRequest = new CreateTriggerRequest(config.SERVICE_NAME, functionName);
    ctRequest.setTriggerType("http");
    ctRequest.setTriggerName("http");
    HttpTriggerConfig httpTriggerConfig = new HttpTriggerConfig(HttpAuthType.ANONYMOUS,
        new HttpMethod[]{HttpMethod.GET});
    ctRequest.setTriggerConfig(httpTriggerConfig);
    ctRequest.setQualifier("LATEST");
    FunctionComputeClient fcClient = config.GetAliCloudClient();
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
      FunctionComputeClient fcClient = config.GetAliCloudClient();
      CreateServiceResponse csResp = fcClient.createService(csReq);
      logger.info("Created service, request ID " + csResp.getRequestId());
    } catch (Exception e) {
      logger.warn("alicloud create service error: " + e.getMessage());
      throw e;
    }
  }

  private String parseInternalUrl(String serviceName, String functionName) {
    return "";
  }

  private String parseUrl(String serviceName, String functionName) {
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

  public void SyncFunction(String functionName) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    BlockingQueue<Float> priceStream = priceSyncCollection.get(functionName);
    if (priceStream == null) {
      priceStream = new ArrayBlockingQueue<>(100);
      priceSyncCollection.put(functionName, priceStream);
    }
    writeLock.unlock();
    // todo set the priceUpstream
    meshInjector.syncFunctionInfo(functionName, config.GetInternalFunctionUrl(functionName),
        config.GetFunctionUrl(functionName), priceStream, null, "alicloud");
  }

  @Override
  public void CreateFunction(String functionName, String codeURL, String runTimeEnvir)
      throws IOException {
    // Create a function

      logger.info("step 1");
      logger.info(config.SERVICE_NAME);
      CreateFunctionRequest cfReq = new CreateFunctionRequest(config.SERVICE_NAME);
      cfReq.setFunctionName(functionName);
      cfReq.setMemorySize(128);
      cfReq.setRuntime(runTimeEnvir);
      cfReq.setTimeout(10);
      cfReq.setInitializationTimeout(3);
    if (config.meshEnable) {
      cfReq.setInitializer(meshInjector.injectInitializer());
      cfReq.setHandler(meshInjector.injectHandler());
      Map<String, String> env = new HashMap<>();
      meshInjector.injectEnv(env, provider, functionName);
      cfReq.setEnvironmentVariables(env);
      logger.info("step 2");
      byte[] zipCode = meshInjector
          .injectMesh(functionName, RunEnvEnum.valueOf(runTimeEnvir), codeURL, provider);
      Code code = new Code().setZipFile(zipCode);
      logger.info("step 3");
      cfReq.setCode(code);
      logger.info("step 4");
    } else {
      cfReq.setHandler("jointfaas.handler");
      byte[] zipCode = prepareCodeZip(codeURL, runTimeEnvir);
      Code code = new Code().setZipFile(zipCode);
      cfReq.setCode(code);
    }
    FunctionComputeClient fcClient = config.GetAliCloudClient();
    try {
      logger.info("step 5");
      CreateFunctionResponse cfResp = fcClient.createFunction(cfReq);
      logger.info("step 6");
      CreateTrigger(functionName);
      logger.info("step 7");
      logger.info("Created function, request ID " + cfResp.getRequestId());
      logger.info("Create function at time: " + cfResp.getCreatedTime());
      SyncFunction(functionName);
      logger.info("step 8");
    } catch (ClientException e) {
      if (e.getErrorCode().equals("ServiceNotFound")) {
        this.CreateService(config.SERVICE_NAME);
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
  }

  @Override
  public String InvokeFunction(String functionName, String jsonString) {

    InvokeFunctionRequest invkReq = new InvokeFunctionRequest(config.SERVICE_NAME, functionName);
    //设置参数
//        String payload = jsonObject.toJSONString();
    invkReq.setPayload(jsonString.getBytes());
    FunctionComputeClient fcClient = config.GetAliCloudClient();
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
    UpdateFunctionRequest ufReq = new UpdateFunctionRequest(config.SERVICE_NAME, functionName);
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
          .injectMesh(functionName, RunEnvEnum.valueOf(runTimeEnvir), codeURL, provider);
      Code code = new Code().setZipFile(zipCode);
      ufReq.setCode(code);
    } else {
      byte[] zipCode = prepareCodeZip(codeURL, runTimeEnvir);
      assert zipCode != null;
      Code code = new Code().setZipFile(zipCode);
      ufReq.setCode(code);
    }

    FunctionComputeClient fcClient = config.GetAliCloudClient();
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
    DeleteFunctionRequest dfRep = new DeleteFunctionRequest(config.SERVICE_NAME, functionName);
    FunctionComputeClient fcClient = config.GetAliCloudClient();
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
    ListFunctionsRequest lfReq = new ListFunctionsRequest(config.SERVICE_NAME);
    FunctionComputeClient fcClient = config.GetAliCloudClient();
    ListFunctionsResponse lfResp = fcClient.listFunctions(lfReq);
    return lfResp;
  }
}