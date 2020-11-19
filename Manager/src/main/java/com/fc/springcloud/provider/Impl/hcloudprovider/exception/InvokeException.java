package com.fc.springcloud.provider.Impl.hcloudprovider.exception;

import jointfaas.worker.InvokeResponse.Code;
import lombok.Getter;

@Getter
public class InvokeException extends RuntimeException {

  private final int code;
  public InvokeException(String message, Code code) {
    super(message);
    this.code = code.getNumber();
  }
}
