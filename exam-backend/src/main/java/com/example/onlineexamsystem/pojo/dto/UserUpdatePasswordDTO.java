package com.example.onlineexamsystem.pojo.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改密码
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdatePasswordDTO {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 3, max = 64, message = "新密码长度必须在3-64之间")
    private String password;
}
