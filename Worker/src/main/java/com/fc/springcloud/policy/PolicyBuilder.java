package com.fc.springcloud.policy;

import java.util.logging.Logger;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PolicyBuilder {

  private static final Logger logger = Logger.getLogger(PolicyBuilder.class.getName());

  static public Policy Build(String policyName) {
    switch (policyName) {
      case "simple": {
        return new SimplePolicy();
      }
      default: {
        logger.info("the " + policyName + " is not existed, use simple instead");
        return new SimplePolicy();
      }
    }
  }
}
