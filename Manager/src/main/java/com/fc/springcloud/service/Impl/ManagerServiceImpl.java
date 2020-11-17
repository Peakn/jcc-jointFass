package com.fc.springcloud.service.Impl;

import com.fc.springcloud.provider.PlatformProvider;
import com.fc.springcloud.service.ManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ManagerServiceImpl implements ManagerService {

    @Autowired
    private PlatformProvider platformProvider;

    public Object CreateFunction(String functionName, String codeDir, String runTimeEnvir, String handler) throws IOException {
        return platformProvider.CreateFunction(functionName, codeDir, runTimeEnvir, handler);
    }

    public Object InvokeFunction(String functionName, String jsonObject) {
        return platformProvider.InvokeFunction(functionName, jsonObject);
    }

    public Object UpdateFunction(String functionName, String codeDir, String runTimeEnvir, String handler) throws IOException {
        return platformProvider.UpdateFunction(functionName, codeDir, runTimeEnvir, handler);
    }

    public Object ListFunction() {
        return platformProvider.ListFunction();
    }

    public Object DeleteFunction(String functionName) {
        return platformProvider.DeleteFunction(functionName);
    }
}
