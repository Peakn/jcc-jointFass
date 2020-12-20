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

    public FunctionComputeClient GetAliCloudClient(){
        Config config = new Config(REGION, accountId, accessKey, accessSecretKey, null, false);
        config.setConnectionRequestTimeoutMillis(400000);
        config.setConnectTimeoutMillis(400000);
        config.setReadTimeoutMillis(400000);
        FunctionComputeClient fcClient = new FunctionComputeClient(config);
        return fcClient;
    }

    @Bean
    public String getRole(){
        return ROLE;
    }
}
