package com.fc.springcloud.pojo.query;


import com.fc.springcloud.pojo.vo.AbstractVoEntity;
import com.fc.springcloud.enums.RunEnvEnum;

/**
 * 云计算函数query
 *
 * @author cossj
 * @date 2020-11-16
 */
public class FunctionQuery extends AbstractVoEntity {
    /**
     * 函数id
     */
    private String functionId;

    /**
     * 函数名称
     */
    private String functionName;

    /**
     * 函数执行内存（单位MB）
     */
    private Integer memorySize;

    /**
     * 区域id
     */
    private String regionId;

    /**
     * 函数运行环境（nodejs、python、java、golang）
     */
    private RunEnvEnum runEnv;

    /**
     * 所在服务
     */
    private String serviceName;

    /**
     * 代码大小（单位：字节）
     */
    private Long codeSize;

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
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

    public Long getCodeSize() {
        return codeSize;
    }

    public void setCodeSize(Long codeSize) {
        this.codeSize = codeSize;
    }
}