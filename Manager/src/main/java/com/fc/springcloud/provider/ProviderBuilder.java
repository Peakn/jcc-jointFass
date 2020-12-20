package com.fc.springcloud.provider;

import com.fc.springcloud.provider.Impl.alicloud.AliCloudProvider;
import com.fc.springcloud.provider.Impl.hcloud.HCloudProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProviderBuilder {

  @Autowired
  AliCloudProvider aliCloudProvider;
  @Autowired
  HCloudProvider hCloudProvider;

  public PlatformProvider Build(ProviderName provider) {
    switch (provider) {
      case ALICLOUD: {
        // init AliCloud here
        return aliCloudProvider;
      }
      case HCLOUD: {
        return hCloudProvider;
      }
    }
    return null;
  }
}
