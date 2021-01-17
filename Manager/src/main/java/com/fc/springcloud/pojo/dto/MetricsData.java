package com.fc.springcloud.pojo.dto;

import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsData {
  String resultType;
  ArrayList<MetricsResult> result;
}
