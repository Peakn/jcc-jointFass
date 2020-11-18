package com.fc.springcloud.common;

import com.fc.springcloud.enums.ResultCode;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * @author tansheng
 * @create 2020/10/23
 */

@Data
@Getter
@Setter
public class CommonResult<T> {
    private Integer code;
    private String message;
    private T data;

    public CommonResult() {
        this.code = 200;
        this.message = "success";
    }

    public CommonResult(String message) {
        this.code = 200;
        this.message = message;
    }

    public CommonResult(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public CommonResult(ResultCode resultCode) {
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public CommonResult(T data) {
        this.code = 200;
        this.message = "success";
        this.data = data;
    }

    public CommonResult(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
}
