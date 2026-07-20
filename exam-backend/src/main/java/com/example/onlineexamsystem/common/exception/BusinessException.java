package com.example.onlineexamsystem.common.exception;


import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    private Integer code;

    /**
     * 构造业务异常
     *
     * @param message 异常消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 构造业务异常（自定义状态码）
     *
     * @param message 异常消息
     * @param code    状态码
     */
    public BusinessException(String message, Integer code) {
        super(message);
        this.code = code;
    }
}
