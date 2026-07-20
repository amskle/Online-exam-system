package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailSendDTO {
    @NotBlank(message = "验证请求不能为空")
    private String challengeId;

    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过254")
    private String email;
}
