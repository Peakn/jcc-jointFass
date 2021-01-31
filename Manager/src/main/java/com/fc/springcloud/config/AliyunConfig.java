package com.fc.springcloud.config;

import com.aliyuncs.fc.client.FunctionComputeClient;
import com.aliyuncs.fc.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AliyunConfig {

    @Value("${mesh.use}")
    public Boolean meshEnable;

    @Value("${aliyun.user.accessKey}")
    private String accessKey;

    @Value("${aliyun.user.accessSecretKey}")
    private String accessSecretKey;

    @Value("${aliyun.user.accountId}")
    private String accountId;

    @Value("${aliyun.service.REGION}")
    private String REGION;

    @Value("${aliyun.service.ROLE}")
    private String ROLE;

    public final String SERVICE_NAME = "demo";

    public FunctionComputeClient GetAliCloudClient(){
        Config config = new Config(REGION, accountId, accessKey, accessSecretKey, null, false);
        config.setConnectionRequestTimeoutMillis(400000);
        config.setConnectTimeoutMillis(400000);
        config.setReadTimeoutMillis(400000);
        FunctionComputeClient fcClient = new FunctionComputeClient(config);
        return fcClient;
    }

    public String GetFunctionUrl(String functionName) {
        // https://1537006230256697.cn-shanghai.fc.aliyuncs.com/2016-08-15/proxy/test/v2_nodejs_A/
        StringBuilder builder = new StringBuilder();
        builder.append("https://");
        builder.append(accountId);
        builder.append(".");
        builder.append(REGION);
        builder.append(".fc.aliyuncs.com/2016-08-15/proxy/");
        builder.append(SERVICE_NAME);
        builder.append("/");
        builder.append(functionName);
        builder.append("/");
        return builder.toString();
    }

    public String GetInternalFunctionUrl(String functionName) {
        return "";
    }

    @Bean
    public String getRole(){
        return ROLE;
    }
}
