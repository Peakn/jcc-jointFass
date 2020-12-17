package com.fc.springcloud.service;

import com.fc.springcloud.pojo.dto.ApplicationDto;
import com.fc.springcloud.pojo.dto.ApplicationDto.StepDto;
import java.util.List;

public interface ApplicationService {
  void CreateApplication(String name, List<StepDto> steps);
  void DeleteApplication(String name);
  List<ApplicationDto> ListApplication();
}
