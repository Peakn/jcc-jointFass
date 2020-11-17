package com.fc.springcloud.config;

import com.aliyuncs.fc.client.FunctionComputeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AliyunConfig {
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

    @Bean
    public FunctionComputeClient initialize(){
        FunctionComputeClient fcClient = new FunctionComputeClient(REGION, accountId, accessKey, accessSecretKey);
        return fcClient;
    }

    @Bean
    public String getRole(){
        return ROLE;
    }
}
