package com.fc.springcloud.pojo.dto;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jointfaas.mesh.model.Model.Application;
import jointfaas.mesh.model.Model.ConditionStep;
import jointfaas.mesh.model.Model.ConditionStep.Condition;
import jointfaas.mesh.model.Model.EndStep;
import jointfaas.mesh.model.Model.FunctionStep;
import jointfaas.mesh.model.Model.ParallelStep;
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

    String stepName;
    String type;
    ConditionStepDto condition;
    FunctionStepDto function;
    ParallelStepDto parallel;
    EndStepDto end;

    public StepDto(Step step) {
      stepName = step.getStepName();
      if (step.getCondition() != null) {
        type = "condition";
        condition = new ConditionStepDto(step.getCondition());
      } else if (step.getEnd() != null) {
        type = "end";
        end = new EndStepDto(step.getEnd());
      } else if (step.getFunction() != null) {
        type = "function";
        function = new FunctionStepDto(step.getFunction());
      } else if (step.getParallel() != null) {
        type = "parallel";
        parallel = new ParallelStepDto(step.getParallel());
      }
    }

    public Step ToStep() {
      switch (type) {
        case "condition": {
          return Step.newBuilder()
              .setCondition(condition.ToConditionStep())
              .setStepName(stepName)
              .build();
        }
        case "end": {
          return Step.newBuilder()
              .setEnd(end.ToEndStep())
              .setStepName(stepName)
              .build();
        }
        case "function": {
          return Step.newBuilder()
              .setFunction(function.ToFunctionStep())
              .setStepName(stepName)
              .build();
        }
        case "parallel": {
          return Step.newBuilder()
              .setParallel(parallel.ToParallelStep())
              .setStepName(stepName)
              .build();
        }
        default: {
          return Step.newBuilder().getDefaultInstanceForType();
        }
      }
    }
  }

  @Getter
  @Setter
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EndStepDto {

    String stepName;

    public EndStepDto(EndStep end) {
      stepName = end.getStepName();
    }

    public EndStep ToEndStep() {
      return EndStep.newBuilder()
          .setStepName(stepName)
          .build();
    }
  }

  @Getter
  @Setter
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ConditionStepDto {

    List<ConditionDto> conditions;

    @Getter
    @Setter
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionDto {

      Integer operator;
      String leftValue;
      String rightValue;
      String defaultNextStep;
      String targetNextStep;

      public ConditionDto(Condition condition) {
        operator = condition.getOperator().getNumber();
        leftValue = condition.getLeftValue();
        rightValue = condition.getRightValue();
        defaultNextStep = condition.getDefaultNextStep();
        targetNextStep = condition.getTargetNextStep();
      }

      public Condition ToCondition() {
        return Condition.newBuilder()
            .setOperatorValue(operator)
            .setLeftValue(leftValue)
            .setRightValue(rightValue)
            .setDefaultNextStep(defaultNextStep)
            .setTargetNextStep(targetNextStep)
            .build();
      }
    }

    public ConditionStepDto(ConditionStep condition) {

      conditions = new ArrayList<>();
      for (Condition cond : condition.getConditionsList()) {
        conditions.add(new ConditionDto(cond));
      }
    }

    public ConditionStep ToConditionStep() {
      List<Condition> protoConditions = new ArrayList<>();
      for (ConditionDto cond : conditions) {
        protoConditions.add(cond.ToCondition());
      }
      return ConditionStep.newBuilder()
          .addAllConditions(protoConditions)
          .build();

    }
  }

  @Getter
  @Setter
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FunctionStepDto {

    String functionName;
    String nextStep;
    String policy;

    public FunctionStepDto(FunctionStep function) {
      functionName = function.getFunctionName();
      nextStep = function.getNextStep();
      policy = function.getPolicy();
    }

    public FunctionStep ToFunctionStep() {
      return FunctionStep.newBuilder()
          .setFunctionName(functionName)
          .setNextStep(nextStep)
          .setPolicy(policy)
          .build();
    }
  }

  @Getter
  @Setter
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ParallelStepDto {

    List<String> targets;
    String nextStep;

    public ParallelStepDto(ParallelStep parallel) {
      targets = new ArrayList<>();
      for (ByteString target : parallel.getTargetsList().asByteStringList()) {
        targets.add(target.toString());
      }
      nextStep = parallel.getNextStep();
    }

    public ParallelStep ToParallelStep() {
      return ParallelStep.newBuilder()
          .setNextStep(nextStep)
          .addAllTargets(targets)
          .build();
    }
  }

  String name;
  String entryStep;
  Map<String, StepDto> steps;

  public ApplicationDto(Application app) {
    this.name = app.getName();
    this.entryStep = app.getEntryStep();
    this.steps = new HashMap<>();
    for (Step step : app.getStepsMap().values()) {
      steps.put(step.getStepName(), new StepDto(step));
    }
  }

  public Application ToApplication() {
    Map<String, Step> protoSteps = new HashMap<>();
    for (StepDto stepDto : steps.values()) {
      protoSteps.put(stepDto.stepName, stepDto.ToStep());
    }
    return Application.newBuilder()
        .setName(name)
        .setEntryStep(entryStep)
        .putAllSteps(protoSteps)
        .build();
  }


}