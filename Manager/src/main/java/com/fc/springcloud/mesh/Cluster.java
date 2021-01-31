package com.fc.springcloud.mesh;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Setter
@Getter
@ToString
public class Cluster {

  List<String> instances;
  String provider;
  String functionName;

  public Cluster(Cluster cluster) {
    this.instances = cluster.instances;
    this.provider = cluster.provider;
    this.functionName = cluster.functionName;
  }
}
