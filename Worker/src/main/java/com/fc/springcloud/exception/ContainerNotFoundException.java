package com.fc.springcloud.exception;

import lombok.Data;

@Data
public class ContainerNotFoundException extends RuntimeException {

  public String containerId;

  public ContainerNotFoundException(String message, String containerId) {
    super(message);
    this.containerId = containerId;
  }
}
