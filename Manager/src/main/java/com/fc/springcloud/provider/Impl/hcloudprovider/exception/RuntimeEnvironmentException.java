package com.fc.springcloud.provider.Impl.hcloudprovider.exception;

import lombok.Getter;

@Getter
public class RuntimeEnvironmentException extends RuntimeException {

  private String image;

  public RuntimeEnvironmentException(String message, String image) {
    super(message);
    this.image = image;
  }
}