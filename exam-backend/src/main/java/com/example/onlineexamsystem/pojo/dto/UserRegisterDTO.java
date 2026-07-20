package com.example.onlineexamsystem.pojo.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterDTO {
    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 30, message = "账号长度必须在3-30")
    private String account;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度必须在6-30")
    private String password;
    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 20, message = "用户名长度必须在1-20")
    private String username;
    @NotNull(message = "请选择用户角色")
    @Min(value = 1, message = "用户角色不正确")
    @Max(value = 2, message = "用户角色不正确")
    private Integer role; // 角色(1.学生，2.教师)
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过254")
    private String email;
}
