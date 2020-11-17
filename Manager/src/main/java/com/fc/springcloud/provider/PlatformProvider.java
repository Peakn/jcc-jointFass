package com.fc.springcloud.provider;

import java.io.IOException;

// PlatformProvider will be init and use in Service
// the implementation choice depends on other callee choose like invoke logic
// we suggest that write PlatformProvider.BuildByProvider(ProviderName provider)
// or a builder like use Lombok

public interface PlatformProvider {
    // define other function here like createFunction
    //createService
    // todo createService is AliProvider logic, implement the function into AliProvider and delete the interface.
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
    // todo thinking about this state should read from database not provider
    public Object ListFunction();
}