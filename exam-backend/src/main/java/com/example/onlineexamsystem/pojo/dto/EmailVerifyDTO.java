package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EmailVerifyDTO {
    @NotBlank(message = "验证请求不能为空")
    private String challengeId;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须是6位数字")
    private String code;

    private boolean trustDevice = true;
}
