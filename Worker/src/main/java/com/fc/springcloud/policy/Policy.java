package com.fc.springcloud.policy;

import com.fc.springcloud.dto.Container;
import java.util.List;

// Policy for choose the best matched container in a scope
public interface Policy {

  Container GetContainer(List<Container> containers);
}
