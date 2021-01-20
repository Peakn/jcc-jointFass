package com.fc.springcloud.provider.Impl.hcloud.exception;

import lombok.Getter;

@Getter
public class CreateContainerException extends RuntimeException {

  private String identity;

  public CreateContainerException(String message, String identity) {
    super(message);
    this.identity = identity;
  }
}
