package com.fc.springcloud.pojo.dto;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsEvent {

  String functionName;
  Double qps;
  Date time;
}
