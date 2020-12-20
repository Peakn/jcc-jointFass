package com.fc.springcloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HCloudConfig {
    @Value("${mesh.use}")
    public Boolean meshEnable;

    @Value("${mesh.gatewayUrl}")
    public String gatewayUrl;

    @Value("${mesh.routerPrefix}")
    public String routerPrefix;
}
