package com.fc.springcloud.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// then same as InitFunctionRequest
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class Resource {

  public String funcName;
  public String image;
  public String runtime;
  public String codeUrI;
  public Long timeout;
  public Long memorySize;
  public Long disk;
  public Resource(jointfaas.worker.InitFunctionRequest request) {
    this.funcName = request.getFuncName();
    this.image = request.getImage();
    this.runtime = request.getRuntime();
    this.codeUrI = request.getCodeURI();
    this.timeout = request.getTimeout();
    this.memorySize = request.getMemorySize();
  }
}
