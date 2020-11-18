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
    PYTHON(0, "python"),
    /**
     * java环境
     */
    JAVA(1, "java"),
    /**
     * nodejs环境
     */
    NODEJS(3, "nodejs"),
    /**
     * golang环境
     */
    GOLANG(4, "python");

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
