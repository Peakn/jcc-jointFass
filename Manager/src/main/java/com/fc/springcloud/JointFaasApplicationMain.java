package com.fc.springcloud;

import com.fc.springcloud.provider.ProviderBuilder;
import com.fc.springcloud.provider.ProviderName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@ComponentScan(basePackages = {"com.fc.springcloud"})
public class JointFaasApplicationMain {

  public static void main(String[] args) {
    SpringApplication.run(JointFaasApplicationMain.class, args);
  }
}
