package com.example.onlineexamsystem.exception;


import lombok.Getter;

/*
 *  业务异常处理
 */
@Getter
public class BusinessException extends RuntimeException{
    private Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(String message, Integer code) {
        super(message);
        this.code = code;
    }
}
