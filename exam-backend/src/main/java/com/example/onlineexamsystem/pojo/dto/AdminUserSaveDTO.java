package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserSaveDTO {
    private Integer id;
    @NotBlank(message = "账号不能为空")
    private String account;
    private String password;
    @NotBlank(message = "用户名不能为空")
    private String username;
    private Integer gender;
    private String phone;
    private Boolean loginStatus;
    @NotNull(message = "角色不能为空")
    private Integer role;
}
