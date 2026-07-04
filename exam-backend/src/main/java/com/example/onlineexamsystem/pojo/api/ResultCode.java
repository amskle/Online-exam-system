package com.example.onlineexamsystem.pojo.api;


import lombok.AllArgsConstructor;
import lombok.Getter;


/**
 * 响应结果码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    NOT_FOUND(404, "资源未找到"),
    UNAUTHORIZED(401, "无权限"),
    FORBIDDEN(403, "禁止访问"),
    BAD_REQUEST(400, "参数错误");

    private final Integer code;
    private final String message;
}
