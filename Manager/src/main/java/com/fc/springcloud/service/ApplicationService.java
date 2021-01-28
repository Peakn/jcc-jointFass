package com.fc.springcloud.service;

import com.fc.springcloud.pojo.dto.ApplicationDto;
import com.fc.springcloud.pojo.dto.ApplicationDto.StepDto;
import java.util.List;
import java.util.Map;

public interface ApplicationService {
  void CreateApplication(String name, String entryStep, Map<String, StepDto> steps);

  void DeleteApplication(String name);
  List<ApplicationDto> ListApplication();
}
