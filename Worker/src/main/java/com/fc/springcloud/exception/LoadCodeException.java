package com.fc.springcloud.exception;

import lombok.Data;

@Data
public class LoadCodeException extends RuntimeException {

  private String containerId;

  public LoadCodeException(String message, String containerId) {
    super(message);
    this.containerId = containerId;
  }
}
