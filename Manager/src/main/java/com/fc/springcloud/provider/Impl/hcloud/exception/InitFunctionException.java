package com.fc.springcloud.provider.Impl.hcloud.exception;

import lombok.Getter;

@Getter
public class InitFunctionException extends RuntimeException {

  private String identity;

  public InitFunctionException(String message, String identity) {
    super(message);
    this.identity = identity;
  }
}
