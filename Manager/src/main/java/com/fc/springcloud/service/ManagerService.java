package com.fc.springcloud.service;

import com.alibaba.fastjson.JSONObject;

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

    public Object CreateFunction(String functionName,String codeDir, String runTimeEnvir, String handler) throws IOException;
    public Object InvokeFunction(String functionName,String jsonObject);
    public Object UpdateFunction(String functionName,String codeDir, String runTimeEnvir, String handler) throws IOException;
    public Object ListFunction();
    public Object DeleteFunction(String functionName);
}
