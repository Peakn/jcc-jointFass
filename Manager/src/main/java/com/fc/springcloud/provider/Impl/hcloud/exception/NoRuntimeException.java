package com.fc.springcloud.provider.Impl.hcloud.exception;

import lombok.Getter;

@Getter
public class NoRuntimeException extends RuntimeException {

  private String identity;

  public NoRuntimeException(String message, String identity) {
    super(message);
    this.identity = identity;
  }
}
