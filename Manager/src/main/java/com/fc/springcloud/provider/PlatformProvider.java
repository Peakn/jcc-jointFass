package com.fc.springcloud.provider;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.fc.client.FunctionComputeClient;

import java.io.IOException;

// PlatformProvider will be init and use in Service
// the implementation choice depends on other callee choose like invoke logic
// we suggest that write PlatformProvider.BuildByProvider(ProviderName provider)
// or a builder like use Lombok

public interface PlatformProvider {
    // define other function here like createFunction
    //createService
    public Object CreateService(String serviceName);
    // create Function
    public Object CreateFunction(String functionName, String codeDir, String runTimeEnvir, String handler) throws IOException;
    // invoke function
    public Object InvokeFunction(String functionName, String jsonObject);
    //update function
    public Object UpdateFunction(String functionName,String codeDir, String runTimeEnvir, String handler) throws IOException;
    //delete function
    public Object DeleteFunction(String functionName);
    //list
    public Object ListFunction();
}