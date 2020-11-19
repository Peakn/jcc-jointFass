package com.fc.springcloud.config;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {
    @Bean
    public Snowflake instance() {
        return IdUtil.createSnowflake(1, 31);
    }
}
