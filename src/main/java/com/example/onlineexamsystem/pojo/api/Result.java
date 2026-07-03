package com.example.onlineexamsystem.pojo.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import javax.naming.InsufficientResourcesException;

/**
 * 通用响应模板
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result <T> {

    private Integer code;
    private String message;
    private T data;
    private Integer count;
    private Long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }
    private Result(Integer code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
    }
    private Result(Integer code, String message, T data, Integer count) {
        this.code = code;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
        this.count = count;
    }

    /*
     * 成功响应 --无数据
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage());
    }
    /*
     * 成功响应 --有数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage(),data);
    }
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }
    public static <T> Result<T> success(T data, Integer count) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, count);
    }
    public static <T> Result<T> success(String message, T data, Integer count) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data, count);
    }




    // 失败响应
    /**
     * 失败 --枚举预定义
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage());
    }
    /**
     * 失败 --自定义消息（默认为500）
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message);
    }
    /**
     * 失败 --自定义状态码和消息
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message);
    }
    /**
     * 失败 --枚举消息
     */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message);
    }







}
