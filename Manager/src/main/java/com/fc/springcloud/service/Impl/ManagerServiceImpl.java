package com.fc.springcloud.service.Impl;

import com.fc.springcloud.pojo.dto.FunctionDto;
import com.fc.springcloud.provider.ProviderBuilder;
import com.fc.springcloud.provider.ProviderName;
import com.fc.springcloud.service.ManagerService;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ManagerServiceImpl implements ManagerService {

  private static final Log logger = LogFactory.getLog(ManagerServiceImpl.class);

  @Autowired
  private ProviderBuilder builder;

  @Value("${mode}")
  private String mode;

  public void CreateFunction(FunctionDto functionDto, String codeURI) throws IOException {
    switch(mode) {
      case "hcloud": {
        try {
          builder.Build(ProviderName.HCLOUD)
              .CreateFunction(functionDto.getFunctionName(), codeURI, functionDto.getRunEnv());
        } catch (Exception e) {
          logger.info("Create function to HCloud error:" + e.getMessage());
        }
        break;
      }
      case "alicloud": {
        try {
          builder.Build(ProviderName.ALICLOUD)
              .CreateFunction(functionDto.getFunctionName(), codeURI, functionDto.getRunEnv());
        } catch (Exception e) {
          logger.info("Create function to AliCloud error:" + e.getMessage());
        }
        break;
      }
      case "mixed": {
        try {
          builder.Build(ProviderName.HCLOUD)
              .CreateFunction(functionDto.getFunctionName(), codeURI, functionDto.getRunEnv());
        } catch (Exception e) {
          logger.info("Create function to HCloud error:" + e.getMessage());
        }

        try {
          builder.Build(ProviderName.ALICLOUD)
              .CreateFunction(functionDto.getFunctionName(), codeURI, functionDto.getRunEnv());
        } catch (Exception e) {
          logger.info("Create function to AliCloud error:" + e.getMessage());
        }
        break;
      }
      default: {
        try {
          builder.Build(ProviderName.HCLOUD)
              .CreateFunction(functionDto.getFunctionName(), codeURI, functionDto.getRunEnv());
        } catch (Exception e) {
          logger.info("Create function to HCloud error:" + e.getMessage());
        }
      }
    }
  }

  public String InvokeFunction(String functionName, String jsonString) {

    Object retVal;
    switch(mode) {
      case "hcloud": {
        retVal = builder.Build(ProviderName.HCLOUD).InvokeFunction(functionName, jsonString);
        return (String) retVal;
      }
      case "alicloud": {
        retVal = builder.Build(ProviderName.ALICLOUD).InvokeFunction(functionName, jsonString);
        return (String) retVal;
      }
      case "mixed": {
        try {
          retVal = builder.Build(ProviderName.HCLOUD).InvokeFunction(functionName, jsonString);
          return (String) retVal;
        } catch (Exception e) {
          logger.warn(e.getMessage());
          retVal = builder.Build(ProviderName.ALICLOUD).InvokeFunction(functionName, jsonString);
          return (String) retVal;
        }
      }
      default: {
        try {
          retVal = builder.Build(ProviderName.HCLOUD).InvokeFunction(functionName, jsonString);
          return (String) retVal;
        } catch (Exception e) {
          logger.warn(e.getMessage());
          throw e;
        }
      }
    }
  }

  public void UpdateFunction(String functionName, String codeDir, String runTimeEnvir) throws IOException {
      builder.Build(ProviderName.ALICLOUD)
        .UpdateFunction(functionName, codeDir, runTimeEnvir);
  }

  public Object ListFunction() {
    return builder.Build(ProviderName.ALICLOUD).ListFunction();
  }

  public void DeleteFunction(String functionName) {
    switch(mode) {
      case "hcloud": {
        builder.Build(ProviderName.HCLOUD).DeleteFunction(functionName);
        break;
      }
      case "alicloud": {
        builder.Build(ProviderName.ALICLOUD).DeleteFunction(functionName);
        break;
      }
      case "mixed": {
        builder.Build(ProviderName.ALICLOUD).DeleteFunction(functionName);
        builder.Build(ProviderName.HCLOUD).DeleteFunction(functionName);
        break;
      }
      default: {
        builder.Build(ProviderName.HCLOUD).DeleteFunction(functionName);
      }
    }
  }
}
