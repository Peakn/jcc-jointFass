package com.fc.springcloud.provider;

import java.io.IOException;

// PlatformProvider will be init and use in Service
// the implementation choice depends on other callee choose like invoke logic
// we suggest that write PlatformProvider.BuildByProvider(ProviderName provider)
// or a builder like use Lombok

public interface PlatformProvider {
    // define other function here like createFunction
    //createService
    // createService is AliProvider logic, implement the function into AliProvider and delete the interface.
    // public Object CreateService(String serviceName);

    // create Function
    public void CreateFunction(String functionName, String codeURI, String runTimeEnvir) throws IOException;

    // invoke function
    public String InvokeFunction(String functionName, String jsonString);

    //update function
    @Deprecated
    public void UpdateFunction(String functionName,String codeDir, String runTimeEnvir) throws IOException;

    //delete function
    public void DeleteFunction(String functionName);

    //list
    // todo thinking about this state should read from database not provider
    @Deprecated
    public Object ListFunction();
}