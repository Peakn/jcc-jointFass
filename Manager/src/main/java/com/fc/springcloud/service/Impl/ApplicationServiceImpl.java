package com.fc.springcloud.service.Impl;

import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.pojo.dto.ApplicationDto;
import com.fc.springcloud.pojo.dto.ApplicationDto.StepDto;
import com.fc.springcloud.provider.Impl.hcloud.HCloudProvider;
import com.fc.springcloud.service.ApplicationService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApplicationServiceImpl implements ApplicationService {


  private static final Logger logger = LoggerFactory.getLogger(ApplicationServiceImpl.class);

  @Autowired
  MeshClient meshClient;

  @Autowired
  HCloudProvider hCloudProvider;


  @Override
  public void CreateApplication(String name, String entryStep, Map<String, StepDto> steps) {

    try {
      meshClient.createApplication(name, entryStep, steps);
    } catch (RuntimeException e) {
      e.printStackTrace();
      throw e;
    }
//    try {
//      hCloudProvider.InitWorkerLoad(s);
//    } catch (RuntimeException e) {
//      logger.error(e.getMessage());
//      throw e;
//    }
  }

  @Override
  public void DeleteApplication(String name) {
    meshClient.deleteApplication(name);
  }

  @Override
  public List<ApplicationDto> ListApplication() {
    return null;
  }
}
