package com.fc.springcloud.common;

public class ErrorResult {
    private String ErrorCode;
    private String ErrorMessage;

    public ErrorResult(String errorCode, String errorMessage) {
        ErrorCode = errorCode;
        ErrorMessage = errorMessage;
    }
}
