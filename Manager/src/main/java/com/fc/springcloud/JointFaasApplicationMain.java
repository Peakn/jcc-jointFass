package com.fc.springcloud;

import com.fc.springcloud.provider.Impl.alicloud.AliCloudProvider;
import com.fc.springcloud.provider.Impl.hcloud.HCloudProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;


@SpringBootApplication
public class JointFaasApplicationMain {

  public static void main(String[] args) {
    ConfigurableApplicationContext context =  SpringApplication.run(JointFaasApplicationMain.class, args);
    HCloudProvider hcloudProvider = context.getBean(HCloudProvider.class);
    hcloudProvider.Start();
    AliCloudProvider aliCloudProvider = context.getBean(AliCloudProvider.class);
    aliCloudProvider.Start();
  }
}
