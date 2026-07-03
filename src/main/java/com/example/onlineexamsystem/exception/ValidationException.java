package com.example.onlineexamsystem.exception;


/*
 * 参数校验异常
 */

import com.example.onlineexamsystem.pojo.api.ResultCode;
import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private Integer code = ResultCode.BAD_REQUEST.getCode();
    public ValidationException(String message) {
        super(message);
    }
    public ValidationException(String message, Integer code) {
        super(message);
        this.code = code;
    }
}
