package com.fc.springcloud.service.Impl;

import com.fc.springcloud.provider.ProviderBuilder;
import com.fc.springcloud.provider.ProviderName;
import com.fc.springcloud.service.ManagerService;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ManagerServiceImpl implements ManagerService {

  @Autowired
  private ProviderBuilder builder;

  public Object CreateFunction(String functionName, String codeDir, String runTimeEnvir,
      String handler) throws IOException {
    return builder.Build(ProviderName.ALICLOUD)
        .CreateFunction(functionName, codeDir, runTimeEnvir, handler);
  }

  public Object InvokeFunction(String functionName, String jsonObject) {
    return builder.Build(ProviderName.ALICLOUD).InvokeFunction(functionName, jsonObject);
  }

  public Object UpdateFunction(String functionName, String codeDir, String runTimeEnvir,
      String handler) throws IOException {
    return builder.Build(ProviderName.ALICLOUD)
        .UpdateFunction(functionName, codeDir, runTimeEnvir, handler);
  }

  public Object ListFunction() {
    return builder.Build(ProviderName.ALICLOUD).ListFunction();
  }

  public Object DeleteFunction(String functionName) {
    return builder.Build(ProviderName.ALICLOUD).DeleteFunction(functionName);
  }
}
