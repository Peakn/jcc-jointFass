package com.fc.springcloud.provider;

// PlatformProvider will be init and use in Service
// the implementation choice depends on other callee choose like invoke logic
// we suggest that write PlatformProvider.BuildByProvider(ProviderName provider)
// or a builder like use Lombok
public interface PlatformProvider {
  public static PlatformProvider BuildByProvider(ProviderName provider) {
    switch (provider) {
      case ALICLOUD: {
        // init AliCloud here
        break;
      }
      case HCLOUD: {
        // init HCloud Provider here, now it is not supported.
        break;
      }
    }
    return null;
  }
  // define other function here like createFunction
}
