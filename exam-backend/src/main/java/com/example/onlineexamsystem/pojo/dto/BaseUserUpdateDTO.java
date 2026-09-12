package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 基础信息修改参数接受DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BaseUserUpdateDTO {
    @NotNull
    private Integer id; // 主键id
    private String avatar; // 头像
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username; // 用户名
    private Integer gender; // 性别(1.男，2.女)
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone; // 电话
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过254")
    private String email; // 邮箱
    private Boolean loginStatus; // 登录状态(0.正常，1.封号)
    private Integer role; // 角色(1.学生，2.教师，3.管理员)
}
