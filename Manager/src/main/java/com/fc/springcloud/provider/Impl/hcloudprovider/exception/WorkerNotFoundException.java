package com.fc.springcloud.provider.Impl.hcloudprovider.exception;

import lombok.Getter;

@Getter
public class WorkerNotFoundException extends RuntimeException {

  private final String identity;

  public WorkerNotFoundException(String message, String identity) {
    super(message);
    this.identity = identity;
  }
}
