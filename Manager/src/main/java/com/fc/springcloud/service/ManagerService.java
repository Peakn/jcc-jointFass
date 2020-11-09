package com.fc.springcloud.service;

import com.aliyuncs.fc.response.ListFunctionsResponse;

import java.io.IOException;
import java.util.Map;

public interface ManagerService {
    /**
     * manager service methods
     * 1. CreateFunction
     * 2. InvokeFunction
     * 3. UpdateFunction
     * 4. ListFunction
     * 5. DeleteFunction
     */

    public String CreateFunction(String functionName,String codeDir, String runTimeEnvir, String handler, int timeout,  int memorySize) throws IOException;
    public String CreateFunction(String functionName,String codeDir, String runTimeEnvir, String handler) throws IOException;
    public String CreateFunction_python3(String functionName,String codeDir, String handler) throws IOException;
    public String CreateFunction_python2_7(String functionName,String codeDir, String handler) throws IOException;
    public String CreateFunction_java8(String functionName,String codeDir, String handler) throws IOException;
    public String InvokeFunction(String functionName);
    public String UpdateFunction(String functionName,String codeDir, String runTimeEnvir, String handler, int timeout,  int memorySize) throws IOException;
    public String UpdateFunction(String functionName,String codeDir, String runTimeEnvir, String handler) throws IOException;
    public String UpdateFunction(String functionName,String codeDir) throws IOException;
    public ListFunctionsResponse ListFunction();
    public String DeleteFunction(String functionName);
    public boolean SetEnvironmentVar(String functionName, Map<String, String> stringStringMap);
}
