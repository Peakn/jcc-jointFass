package com.fc.springcloud.service.Impl;

import com.alibaba.fastjson.JSONObject;
import com.fc.springcloud.provider.AliCloudProvider;
import com.fc.springcloud.provider.PlatformProvider;
import com.fc.springcloud.service.ManagerService;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ManagerServiceImpl implements ManagerService {

    private static final Log logger = LogFactory.getLog(ManagerService.class);

    private static final String REGION = "cn-hangzhou";
    private static final String SERVICE_NAME = "demo";
    private static final String ROLE = "acs:ram::1727804214750599:role/AliyunFCLogExecutionRole";

    private PlatformProvider platformProvider = new AliCloudProvider();

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
