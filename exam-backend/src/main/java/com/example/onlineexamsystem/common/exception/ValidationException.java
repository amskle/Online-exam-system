package com.example.onlineexamsystem.common.exception;


/*
 * 参数校验异常
 */

import com.example.onlineexamsystem.pojo.api.ResultCode;
import lombok.Getter;

/**
 * 参数校验异常
 */
@Getter
public class ValidationException extends RuntimeException {
    private Integer code = ResultCode.BAD_REQUEST.getCode();

    /**
     * 构造参数校验异常
     *
     * @param message 异常消息
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * 构造参数校验异常（自定义状态码）
     *
     * @param message 异常消息
     * @param code    状态码
     */
    public ValidationException(String message, Integer code) {
        super(message);
        this.code = code;
    }
}
