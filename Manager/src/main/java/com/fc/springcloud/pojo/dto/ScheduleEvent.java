package com.fc.springcloud.pojo.dto;

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
public class ScheduleEvent {
  String functionName;
  ScheduleAction action;
  Integer target;
}
