package com.fc.springcloud.provider.Impl.hcloud.exception;

public class NoWorkerException extends RuntimeException {
  public NoWorkerException(String message) {
    super(message);
  }
}
