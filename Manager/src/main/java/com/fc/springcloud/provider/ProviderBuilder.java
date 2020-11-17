package com.fc.springcloud.provider;

import com.fc.springcloud.provider.Impl.AliCloudProvider;
import com.fc.springcloud.provider.Impl.HCloudProvider;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
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
