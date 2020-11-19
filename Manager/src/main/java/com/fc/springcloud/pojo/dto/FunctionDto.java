package com.fc.springcloud.pojo.dto;


import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.pojo.vo.AbstractVoEntity;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * 云计算函数dto
 *
 * @author cossj
 * @date 2020-11-19
 */
public class FunctionDto extends AbstractVoEntity {
    /**
     * 函数名称
     */
    @NotEmpty(message = "functionName can not be empty")
    private String functionName;

    /**
     * 函数入口
     */
    private String handler;

    /**
     * 单实例并发度
     */
    private Integer instanceConcurrency;

    /**
     * 函数实例类型（弹性实例、性能实例）
     */
    private String instanceType;

    /**
     * 函数执行内存（单位MB）
     */
    @Min(value = 128, message = "ContainerMemory is too small (min: 128)")
    private Integer memorySize;

    /**
     * 区域id
     */
    private String regionId;

    /**
     * 函数运行环境（nodejs、python、java、golang）
     */
    @NotNull(message = "Please select the container operating environment")
    private RunEnvEnum runEnv;

    /**
     * 所在服务
     */
    private String serviceName;

    /**
     * 描述
     */
    private String description;

    /**
     * 函数执行超时时间（单位：秒）
     */
    @Min(value = 1, message = "Timeout is too small (min: 1)")
    private Integer timeout;

    /**
     * 函数初始化入口
     */
    private String initializer;

    /**
     * 函数初始化超时时间（单位：秒）
     */
    private Integer initializationTimeout;

    /**
     * 代码大小（单位：字节）
     */
    private Long codeSize;

    /**
     * @return base64编码的文件
     */
    @NotNull(message = "")
    private CodeBase64 code;

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public Integer getInstanceConcurrency() {
        return instanceConcurrency;
    }

    public void setInstanceConcurrency(Integer instanceConcurrency) {
        this.instanceConcurrency = instanceConcurrency;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public Integer getMemorySize() {
        return memorySize;
    }

    public void setMemorySize(Integer memorySize) {
        this.memorySize = memorySize;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public RunEnvEnum getRunEnv() {
        return runEnv;
    }

    public void setRunEnv(RunEnvEnum runEnv) {
        this.runEnv = runEnv;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getInitializer() {
        return initializer;
    }

    public void setInitializer(String initializer) {
        this.initializer = initializer;
    }

    public Integer getInitializationTimeout() {
        return initializationTimeout;
    }

    public void setInitializationTimeout(Integer initializationTimeout) {
        this.initializationTimeout = initializationTimeout;
    }

    public Long getCodeSize() {
        return codeSize;
    }

    public void setCodeSize(Long codeSize) {
        this.codeSize = codeSize;
    }

    public CodeBase64 getCode() {
        return code;
    }

    public void setCode(CodeBase64 code) {
        this.code = code;
    }
}