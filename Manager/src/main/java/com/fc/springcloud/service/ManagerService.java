package com.fc.springcloud.service;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.fc.client.FunctionComputeClient;
import com.fc.springcloud.common.CommonResult;
import org.hibernate.validator.constraints.time.DurationMax;

import java.io.IOException;

public interface ManagerService {
    /**
     * manager service methods
     * 1. CreateFunction
     * 2. InvokeFunction
     * 3. UpdateFunction
     * 4. ListFunction
     * 5. DeleteFunction
     */

    //If createFunction error, it throws an exception. Otherwise, it succeeds and nothing needs to be return
    public void CreateFunction(String functionName, String codeDir, String runTimeEnvir) throws IOException;

    //Directly return the results
    public String InvokeFunction(String functionName,String jsonString);

    //If UpdateFunction error, it throws an exception. Otherwise, it succeeds and nothing needs to be return
    @Deprecated
    public void UpdateFunction(String functionName,String codeDir, String runTimeEnvir) throws IOException;

    //If DeleteFunction error, it throws an exception. Otherwise, it succeeds and nothing needs to be return
    public void DeleteFunction(String functionName);


    @Deprecated
    public Object ListFunction();


}
