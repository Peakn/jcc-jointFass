package com.fc.springcloud.exception;

/**
 * 超出业务范围的异常
 */
public class OutOfBusinessException extends RuntimeException {

    private Integer code;

    private String msg;


    public OutOfBusinessException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public OutOfBusinessException(String msg, Throwable e) {
        super(msg, e);
        this.msg = msg;
    }

    public OutOfBusinessException(String msg, int code) {
        super(msg);
        this.msg = msg;
        this.code = code;
    }

    public OutOfBusinessException(String msg, int code, Throwable e) {
        super(msg, e);
        this.msg = msg;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
