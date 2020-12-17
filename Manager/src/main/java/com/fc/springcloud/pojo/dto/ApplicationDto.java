package com.fc.springcloud.pojo.dto;

import java.util.ArrayList;
import java.util.List;
import jointfaas.mesh.model.Model.Application;
import jointfaas.mesh.model.Model.Step;
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
public class ApplicationDto {

  @Getter
  @Setter
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StepDto {

    String functionName;
  }

  String name;
  List<StepDto> stepChains;

  public ApplicationDto(Application app) {
    this.name = app.getName();
    List<StepDto> stepDtos = new ArrayList<>();
    for (Step step : app.getStepChainsList()) {
      stepDtos.add(new StepDto(step.getFunctionName()));
    }
    this.stepChains = stepDtos;
  }


}