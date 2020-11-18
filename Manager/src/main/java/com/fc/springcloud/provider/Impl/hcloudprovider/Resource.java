package com.fc.springcloud.provider.Impl.hcloudprovider;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Resource {

  String funcName;
  String image;
  String runtime;
  String codeURI;
  int memorySize;
  int timeout;
}