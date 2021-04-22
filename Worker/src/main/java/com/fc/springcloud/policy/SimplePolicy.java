package com.fc.springcloud.policy;

import com.fc.springcloud.dto.Container;
import java.util.List;

public class SimplePolicy implements Policy {

  @Override
  public Container GetContainer(List<Container> containers) {
    Container chosenContainer = null;
    for (Container container : containers) {
      // now choose the first one
      chosenContainer = container;
      break;
    }
    return chosenContainer;
  }
}
