package com.fc.springcloud.service;

import com.fc.springcloud.pojo.dto.PolicyDto;

public interface PolicyService {

  void CreatePolicy(PolicyDto policyDto);

  void UpdatePolicy(PolicyDto policyDto);

  void DeletePolicy(String name);

  PolicyDto GetPolicy(String name);
}
