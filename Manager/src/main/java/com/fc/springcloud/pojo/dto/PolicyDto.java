package com.fc.springcloud.pojo.dto;

import jointfaas.mesh.model.Model.Policy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PolicyDto {
  String name;
  String type;
  String metaData;

  public PolicyDto(Policy policy) {
    name = policy.getName();
    type = policy.getType();
    metaData = policy.getMetaData();
  }

  public Policy ToPolicy() {
    return Policy.newBuilder()
        .setMetaData(metaData)
        .setType(type)
        .setName(name)
        .build();
  }
}
