package com.fc.springcloud.enums;

/**
 * 性别枚举类
 *
 * @author : zhangjie
 * @date : 2019/3/21
 */
public enum RunEnvEnum implements BaseEnum {
    /**
     * python环境
     */
    python3(0, "python3"),
    /**
     * java环境
     */
    java8(1, "java8"),
    /**
     * nodejs环境
     */
    nodejs(3, "nodejs"),
    /**
     * golang环境
     */
    golang(4, "python");

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

    RunEnvEnum(Integer value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }
}
