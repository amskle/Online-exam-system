package com.example.onlineexamsystem.pojo.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Integer role; // 角色(1.学生，2.教师)
}
