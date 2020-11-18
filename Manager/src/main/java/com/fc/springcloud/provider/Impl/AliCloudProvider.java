package com.fc.springcloud.provider.Impl;

import com.aliyuncs.fc.client.FunctionComputeClient;
import com.aliyuncs.fc.exceptions.ClientException;
import com.aliyuncs.fc.model.Code;
import com.aliyuncs.fc.request.*;
import com.aliyuncs.fc.response.*;
import com.fc.springcloud.config.AliyunConfig;
import com.fc.springcloud.provider.PlatformProvider;
import com.fc.springcloud.service.ManagerService;
import com.fc.springcloud.util.ZipUtil;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
@NoArgsConstructor
public class AliCloudProvider implements PlatformProvider {
    private static final Log logger = LogFactory.getLog(AliCloudProvider.class);

    private static final String SERVICE_NAME = "demo";

    @Autowired
    AliyunConfig config;

    @Autowired
    private FunctionComputeClient fcClient;
    @Autowired
    private String role;

    //Create Service
    public Object CreateService(String serviceName){
        //Create Service
        CreateServiceRequest csReq = new CreateServiceRequest();
        csReq.setServiceName(serviceName);
        csReq.setDescription("The service aims at jointFaas.");
        csReq.setRole(role);
        CreateServiceResponse csResp = fcClient.createService(csReq);
        logger.info("Created service, request ID " + csResp.getRequestId());
        return csResp;
    }

    private byte[] prepareCodeZip(String codeURI, String runtime){
        File[] addFiles = new File[1];
        try {
            File tmpFile = File.createTempFile("tmpFunctionFile",".zip");
            FileUtils.copyURLToFile(new URL(codeURI), tmpFile);
            if (runtime == "python3")
                //append alicloud specific entrypoint into the zipfile
                addFiles[0] = ResourceUtils.getFile("classpath:static/aliCloud/python3/jointfaas.py");
            ZipUtil.addFilesToExistingZip(tmpFile, addFiles);
            return Files.readAllBytes(Paths.get(tmpFile.getPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Object CreateFunction(String functionName, String codeURI, String runTimeEnvir, String handler) throws IOException {
        // Create a function
        CreateFunctionRequest cfReq = new CreateFunctionRequest(SERVICE_NAME);
        cfReq.setFunctionName(functionName);
        cfReq.setDescription("Function for test");
        cfReq.setMemorySize(128);
        cfReq.setRuntime(runTimeEnvir);
        cfReq.setHandler("jointfaas.handler");

        byte[] zipCode = prepareCodeZip(codeURI, runTimeEnvir);
        assert zipCode != null;
        Code code = new Code().setZipFile(zipCode);
        cfReq.setCode(code);

        try {
            CreateFunctionResponse cfResp = fcClient.createFunction(cfReq);
            logger.info("Created function, request ID " + cfResp.getRequestId());
            logger.info("Create function at time: " + cfResp.getCreatedTime());
            return cfResp;
        }
        catch (ClientException e){
            if(e.getErrorCode().equals("ServerNotFound")){
                this.CreateService(SERVICE_NAME);
                this.CreateFunction(functionName, codeURI, runTimeEnvir, handler);
                logger.info("The Service not exist.we have recreate service and function");
            }
            else if(e.getErrorCode().equals("FunctionAlreadyExists")){
                this.UpdateFunction(functionName, codeURI, runTimeEnvir, handler);
                logger.info("The function has existed, and has been updated.");
            }
            else {
                logger.info("The function create fail.=, message: " + e.getMessage() + "\n" + e.getErrorMessage() + "\n" + e.getErrorCode());
            }
            return e;
        }
    }

    @Override
    public Object InvokeFunction(String functionName, String jsonObject) {

        InvokeFunctionRequest invkReq = new InvokeFunctionRequest(SERVICE_NAME, functionName);

        //设置参数
//        String payload = jsonObject.toJSONString();
        invkReq.setPayload(jsonObject.getBytes());

        InvokeFunctionResponse invkResp = fcClient.invokeFunction(invkReq);
        logger.info("Function invoke success, requestedId: " + invkResp.getRequestId());
        logger.info("Run result：" + new String(invkResp.getContent()));
        String result = "Function invoke success, requestedId: " + invkResp.getRequestId() + ".Run result：" +  new String(invkResp.getContent());
        logger.info(result);
        return invkResp;
    }

    @Override
    public Object UpdateFunction(String functionName, String codeURI, String runTimeEnvir, String handler) throws IOException {
        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
        ufReq.setDescription("Update Function");

        ufReq.setRuntime(runTimeEnvir);
        ufReq.setHandler("jointfaas.handler");
        //更新代码
        byte[] zipCode = prepareCodeZip(codeURI, runTimeEnvir);
        assert zipCode != null;
        Code code = new Code().setZipFile(zipCode);
        ufReq.setCode(code);

        try {
            UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
            logger.info("Update function configurations and code success, the request id:" + ufResp.getFunctionId());
            return ufResp;
        }
        catch (ClientException e){
            if(e.getErrorCode().equals("FunctionNotFound")) {
                logger.info("Function updateFail.This function not exists, please check functionName or create.");
            }
            else
                logger.info("Function update fail." + e.getErrorCode());
            return e;
        }
    }

    @Override
    public Object DeleteFunction(String functionName) {
        DeleteFunctionRequest dfRep = new DeleteFunctionRequest(SERVICE_NAME, functionName);
        try {
            DeleteFunctionResponse dfResp = fcClient.deleteFunction(dfRep);
            logger.info("Function Delete Success.Delete function success, the requested id: " + dfResp.getRequestId());
            return dfResp;
        } catch (ClientException e) {
            if(e.getErrorCode().equals("FunctionNotFound")){
                logger.info("Function Delete Fail.This function not exist, please check functionName");
            }
            else {
                e.printStackTrace();
            }
            return e;
        }
    }

    @Override
    public Object ListFunction() {
        ListFunctionsRequest lfReq = new ListFunctionsRequest(SERVICE_NAME);
        ListFunctionsResponse lfResp = fcClient.listFunctions(lfReq);
        return lfResp;
    }
}