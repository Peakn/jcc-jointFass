package com.fc.springcloud.service.Impl;

import com.fc.springcloud.provider.ProviderBuilder;
import com.fc.springcloud.provider.ProviderName;
import com.fc.springcloud.service.ManagerService;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ManagerServiceImpl implements ManagerService {

  private static final Log logger = LogFactory.getLog(ManagerServiceImpl.class);

  @Autowired
  private ProviderBuilder builder;

  public void CreateFunction(String functionName, String codeURI, String runTimeEnvir) throws IOException {
    try {
      builder.Build(ProviderName.HCLOUD)
          .CreateFunction(functionName, codeURI, runTimeEnvir);
    } catch (Exception e) {
        logger.info("Create function to HCloud error:" + e.getMessage());
    }

//    try {
//      builder.Build(ProviderName.ALICLOUD)
//          .CreateFunction(functionName, codeURI, runTimeEnvir);
//    } catch (Exception e) {
//      logger.info("Create function to AliCloud error:" + e.getMessage());
//    }
  }

  public String InvokeFunction(String functionName, String jsonString) {
    Object retVal;
    try {
      retVal = builder.Build(ProviderName.HCLOUD).InvokeFunction(functionName, jsonString);
      return (String) retVal;
    } catch (Exception e) {
      logger.warn(e.getMessage());
      retVal = builder.Build(ProviderName.ALICLOUD).InvokeFunction(functionName, jsonString);
      return (String) retVal;
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
      builder.Build(ProviderName.ALICLOUD).DeleteFunction(functionName);
      builder.Build(ProviderName.HCLOUD).DeleteFunction(functionName);
  }
}
