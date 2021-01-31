package com.fc.springcloud.service;

import com.fc.springcloud.pojo.dto.FunctionDto;
import java.io.IOException;

public interface ManagerService {
    /**
     * manager service methods
     * 1. CreateFunction
     * 2. InvokeFunction
     * 3. UpdateFunction
     * 4. ListFunction
     * 5. DeleteFunction
     * @param functionDto
     * @param codeUrl
     */

    //If createFunction error, it throws an exception. Otherwise, it succeeds and nothing needs to be return
    public void CreateFunction(FunctionDto functionDto, String codeUrl) throws IOException;

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
