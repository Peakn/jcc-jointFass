package com.fc.springcloud.service.Impl;

import com.aliyuncs.fc.client.FunctionComputeClient;
import com.aliyuncs.fc.exceptions.ClientException;
import com.aliyuncs.fc.model.Code;
import com.aliyuncs.fc.request.*;
import com.aliyuncs.fc.response.*;
import com.fc.springcloud.service.ManagerService;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class ManagerServiceImpl implements ManagerService {

    private static final Log logger = LogFactory.getLog(ManagerService.class);

    private static final String REGION = "cn-hangzhou";
    private static final String SERVICE_NAME = "demo";
    private static final String ROLE = "acs:ram::1727804214750599:role/AliyunFCLogExecutionRole";

    //initialize the FunctionClient
    private FunctionComputeClient initialize(){
        String accessKey = "1234";
        String accessSecretKey = "5678";
        String accountId = "0000";

        // Initialize FC client
        FunctionComputeClient fcClient = new FunctionComputeClient(REGION, accountId, accessKey, accessSecretKey);
        return fcClient;
    }
    //Create Service
    public void CreateService(String serviceName){
        //Initialize FC Client
        FunctionComputeClient fcClient = this.initialize();

        //Create Service
        CreateServiceRequest csReq = new CreateServiceRequest();
        csReq.setServiceName(serviceName);
        csReq.setDescription("The service aims at jointFass.");
        csReq.setRole(ROLE);
        CreateServiceResponse csResp = fcClient.createService(csReq);
        logger.info("Created service, request ID " + csResp.getRequestId());
    }

    public String CreateFunction(String functionName, String codeDir, String runTimeEnvir, String handler, int timeout, int memorySize) throws IOException {
        FunctionComputeClient fcClient = this.initialize();
        String result = null;

        // Create a function
        CreateFunctionRequest cfReq = new CreateFunctionRequest(SERVICE_NAME);
        cfReq.setFunctionName(functionName);
        cfReq.setDescription("Function for test");
        cfReq.setMemorySize(memorySize);
        cfReq.setRuntime(runTimeEnvir);
        cfReq.setHandler(handler);

        // Used in initializer situations.
        Code code = new Code().setDir(codeDir);
        cfReq.setCode(code);
        cfReq.setTimeout(timeout);

//        //setEnvironmentVariables
//        Map<String, String> environmentVar = new HashMap<String, String>();
//        environmentVar.put("man","chen");
//        environmentVar.put("girl","xiao");
//        cfReq.setEnvironmentVariables(environmentVar);
//        System.out.println(environmentVar);

        try {
            CreateFunctionResponse cfResp = fcClient.createFunction(cfReq);
            logger.info("Created function, request ID " + cfResp.getRequestId());
            result =  "Function creates success.Created function, request ID: " + cfResp.getRequestId();
        }
        catch (ClientException e){
            if(e.getErrorCode().equals("ServerNotFound")){
                this.CreateService(SERVICE_NAME);
                this.CreateFunction(functionName, codeDir, runTimeEnvir, handler, timeout, memorySize);
                logger.info("The Service not exist.we have recreate service and function");
                result = "Function Create Success.The Service not exist.we have recreate service and function";
            }
            else if(e.getErrorCode().equals("FunctionAlreadyExists")){
                this.UpdateFunction(functionName, codeDir, runTimeEnvir, handler, timeout, memorySize);
                logger.info("The function has existed, and has been updated.");
                result = "The function has existed, and has been updated.";
            }
            else {
                logger.info("The function create fail." + e.getErrorCode());
                result = "The function create fail." + e.getErrorCode();
            }
        }

       return result;
    }

    public String CreateFunction(String functionName, String codeDir, String runTimeEnvir, String handler) throws IOException {
        return this.CreateFunction(functionName, codeDir, runTimeEnvir, handler, 60, 128);
    }

    public String CreateFunction_python3(String functionName, String codeDir, String handler) throws IOException {
        return this.CreateFunction(functionName, codeDir, "python3", handler);
    }

    public String CreateFunction_python2_7(String functionName, String codeDir, String handler) throws IOException {
        return this.CreateFunction(functionName, codeDir, "python2.7", handler);
    }

    public String CreateFunction_java8(String functionName, String codeDir, String handler) throws IOException {
        return this.CreateFunction(functionName, codeDir, "java8", handler);
    }

    public String InvokeFunction(String functionName) {
        FunctionComputeClient fcClient = this.initialize();

        InvokeFunctionRequest invkReq = new InvokeFunctionRequest(SERVICE_NAME, functionName);
        String payload = "Hello FunctionCompute!";
        invkReq.setPayload(payload.getBytes());
        InvokeFunctionResponse invkResp = fcClient.invokeFunction(invkReq);
        logger.info("Function invoke success, requestedId: " + invkResp.getRequestId());
        logger.info("Run result：" + invkResp.getContent());
        String result = "Function invoke success, requestedId: " + invkResp.getRequestId() + ".Run result：" + invkResp.getContent();
        logger.info(result);
        return result;
    }

    public String UpdateFunction(String functionName, String codeDir, String runTimeEnvir, String handler, int timeout, int memorySize) throws IOException {
        FunctionComputeClient fcClient = this.initialize();
        String result = null;

        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
        ufReq.setDescription("Update Function");
        ufReq.setRuntime(runTimeEnvir);
        ufReq.setHandler(handler);
        ufReq.setTimeout(timeout);
        ufReq.setMemorySize(memorySize);
        //更新代码
        Code code = new Code().setDir(codeDir);
        ufReq.setCode(code);

        try {
            UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
            logger.info("Update function configurations and code success, the request id:" + ufResp.getFunctionId());
            result = "Function update success.Update function configurations and code success, the request id:" + ufResp.getFunctionId();
        }
        catch (ClientException e){
            if(e.getErrorCode().equals("FunctionNotFound")) {
                logger.info("Function updateFail.This function not exists, please check functionName or create.");
                result = "Function update Fail.This function not exists, please check functionName or create.";
            }
            else
                result = "Function update fail." + e.getErrorCode();
        }
        return result;
    }

    public String UpdateFunction(String functionName, String codeDir, String runTimeEnvir, String handler) throws IOException {
        FunctionComputeClient fcClient = this.initialize();
        String result = null;

        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
        ufReq.setDescription("Update Function");
        ufReq.setRuntime(runTimeEnvir);
        ufReq.setHandler(handler);
        //更新代码
        Code code = new Code().setDir(codeDir);
        ufReq.setCode(code);

        try {
            UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
            logger.info("Update function configurations and code success, the request id:" + ufResp.getFunctionId());
            result = "Function update success.Update function configurations and code success, the request id:" + ufResp.getFunctionId();
        }
        catch (ClientException e){
            if(e.getErrorCode().equals("FunctionNotFound")) {
                logger.info("Function updateFail.This function not exists, please check functionName or create.");
                result = "Function update Fail.This function not exists, please check functionName or create.";
            }
            else
                result = "Function update fail." + e.getErrorCode();
        }
        return result;
    }

    public String UpdateFunction(String functionName, String codeDir) throws IOException {
        FunctionComputeClient fcClient = this.initialize();
        String result = null;

        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
        ufReq.setDescription("Update Function");
        //更新代码
        Code code = new Code().setDir(codeDir);
        ufReq.setCode(code);

        try {
            UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
            logger.info("Update function configurations and code success, the request id:" + ufResp.getFunctionId());
            result = "Function update success.Update function configurations and code success, the request id:" + ufResp.getFunctionId();
        }
        catch (ClientException e){
            if(e.getErrorCode().equals("FunctionNotFound")) {
                logger.info("Function updateFail.This function not exists, please check functionName or create.");
                result = "Function update Fail.This function not exists, please check functionName or create.";
            }
            else
                result = "Function update fail." + e.getErrorCode();
        }
        return result;
    }

    public ListFunctionsResponse ListFunction() {
        FunctionComputeClient fcClient = this.initialize();
        String result = null;

        ListFunctionsRequest lfReq = new ListFunctionsRequest(SERVICE_NAME);
        ListFunctionsResponse lfResp = fcClient.listFunctions(lfReq);
        return lfResp;
    }

    public String DeleteFunction(String functionName) {
        FunctionComputeClient fcClient = this.initialize();
        String result = null;

        DeleteFunctionRequest dfRep = new DeleteFunctionRequest(SERVICE_NAME, functionName);
        try {
            DeleteFunctionResponse dfResp = fcClient.deleteFunction(dfRep);
            logger.info("Function Delete Success.Delete function success, the requested id: " + dfResp.getRequestId());
            result = "Function Delete Success.Delete function success, the requested id: " + dfResp.getRequestId();
        } catch (ClientException e) {
            if(e.getErrorCode().equals("FunctionNotFound")){
                logger.info("Function Delete Fail.This function not exist, please check functionName");
                result = "Function Delete Fail.This function not exist, please check functionName";
            }
            else {
                e.printStackTrace();
                result = "Function Delete Fail." + e.getErrorCode();
            }
        }
        return result;
    }

    /**
     * Set Environment Variable（Event）
     * @param functionName
     * @param environmentVar type of map
     * @return success or failure of operation
     */
    public boolean SetEnvironmentVar(String functionName, Map<String, String> environmentVar) {
        FunctionComputeClient fcClient = this.initialize();

        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
        ufReq.setEnvironmentVariables(environmentVar);
        try {
            UpdateFunctionResponse ufResp = fcClient.updateFunction(ufReq);
            System.out.println(ufReq.getEnvironmentVariables());
            return true;
        }
        catch (ClientException e) {
            e.printStackTrace();
            return false;
        }
    }

    //GetEnvironmentVariable
    public Map<String, String> GetEnvironmentVar(String functionName){
        FunctionComputeClient fcClient = this.initialize();
        UpdateFunctionRequest ufReq = new UpdateFunctionRequest(SERVICE_NAME, functionName);
        Map<String, String> var = ufReq.getEnvironmentVariables();
        return var;
    }
}
