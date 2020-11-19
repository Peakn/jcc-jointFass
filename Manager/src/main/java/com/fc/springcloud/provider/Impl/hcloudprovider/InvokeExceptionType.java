package com.fc.springcloud.provider.Impl.hcloudprovider;

public enum InvokeExceptionType {
  NO_SUCH_FUNCTION(1),
  RUNTIME_ERROR (2),
  RETRY(3);

  private int number;
  InvokeExceptionType(int i) {
    number = 1;
  }
}
