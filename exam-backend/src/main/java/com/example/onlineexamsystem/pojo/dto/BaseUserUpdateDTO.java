package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String username; // 用户名
    private Integer gender; // 性别(1.男，2.女)
    private String phone; // 电话
    private Boolean loginStatus; // 登录状态(0.正常，1.封号)
    private Integer role; // 角色(1.学生，2.教师，3.管理员)
}
