package com.fc.springcloud.enums;

public enum ResultCode {
    OK(200, "正常返回"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "未找到"),
    EXISTS(405, "资源已存在"),
    OUT_OF_BUSINESS(409, "超出业务范围"),
    INTERNAL_SERVER_ERROR(500, "服务器发生错误");
    //9XXX 其他自定义错误, 视具体接口而定

    private int code;
    private String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
