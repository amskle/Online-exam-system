package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员用户保存/更新参数
 */
@Data
public class AdminUserSaveDTO {
    private Integer id;
    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 50, message = "账号长度必须在3-50之间")
    private String account;
    @Pattern(regexp = "^$|^.{6,64}$", message = "密码长度必须在6-64之间")
    private String password;
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;
    private Integer gender;
    private String phone;
    private Boolean loginStatus;
    @NotNull(message = "角色不能为空")
    @Min(value = 1, message = "角色不正确")
    @Max(value = 3, message = "角色不正确")
    private Integer role;
}
