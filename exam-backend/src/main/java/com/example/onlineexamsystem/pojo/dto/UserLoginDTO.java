package com.example.onlineexamsystem.pojo.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDTO {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 30, message = "账号长度必须在3-30")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
}
