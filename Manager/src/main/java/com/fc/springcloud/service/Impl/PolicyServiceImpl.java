package com.fc.springcloud.service.Impl;

import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.pojo.dto.PolicyDto;
import com.fc.springcloud.service.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PolicyServiceImpl implements PolicyService {


  private static final Logger logger = LoggerFactory.getLogger(ApplicationServiceImpl.class);

  @Autowired
  MeshClient meshClient;

  @Override
  public void CreatePolicy(PolicyDto policyDto) {
    meshClient.createPolicy(policyDto);
  }

  @Override
  public void UpdatePolicy(PolicyDto policyDto) {
    meshClient.updatePolicy(policyDto);
  }

  @Override
  public void DeletePolicy(String name) {
    meshClient.deletePolicy(name);
  }

  @Override
  public PolicyDto GetPolicy(String name) {
    return meshClient.getPolicy(name);
  }
}
