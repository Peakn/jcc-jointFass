package com.fc.springcloud.pojo.dto;

import com.fc.springcloud.enums.BaseEnum;

public enum ScheduleAction implements BaseEnum {
  /**
   * create container
   */
  create(0, "create"),
  /**
   * delete container
   */
  delete(1, "delete");

  private final Integer value;

  private final String displayName;

  @Override
  public Integer getValue() {
    return value;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  ScheduleAction(Integer value, String displayName) {
    this.value = value;
    this.displayName = displayName;
  }
}
