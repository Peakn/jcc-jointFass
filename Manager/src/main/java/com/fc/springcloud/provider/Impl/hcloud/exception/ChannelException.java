package com.fc.springcloud.provider.Impl.hcloud.exception;

import lombok.Getter;

@Getter
public class ChannelException extends RuntimeException {

  private String identity;

  public ChannelException(String message, String identity) {
    super(message);
    this.identity = identity;
  }
}