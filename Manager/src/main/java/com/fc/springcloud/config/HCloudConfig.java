package com.fc.springcloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HCloudConfig {
    @Value("${mesh.use}")
    public Boolean meshEnable;

    @Value("${mesh.externalGatewayLocation}")
    public String externalGatewayLocation;

    @Value("${mesh.internalGatewayLocation}")
    public String internalGatewayLocation;

    @Value("${mesh.routerPrefix}")
    public String routerPrefix;
}
