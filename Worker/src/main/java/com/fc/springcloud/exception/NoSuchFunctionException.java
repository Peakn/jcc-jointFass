package com.fc.springcloud.exception;

import lombok.Data;

@Data
public class NoSuchFunctionException extends RuntimeException {

  public String functionName;

  public NoSuchFunctionException(String message, String functionName) {
    super(message);
    this.functionName = functionName;
  }
}
