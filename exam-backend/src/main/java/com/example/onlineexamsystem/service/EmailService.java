package com.example.onlineexamsystem.service;

import com.example.onlineexamsystem.pojo.dto.EmailSendDTO;
import com.example.onlineexamsystem.pojo.dto.EmailVerifyDTO;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

public interface EmailService {
    UserLoginResponseVO beginLogin(UserLoginDTO dto, Map<Integer, String> trustedDeviceTokens,
                                   HttpServletResponse response);

    UserLoginResponseVO beginRegister(UserRegisterDTO dto);

    UserLoginResponseVO sendCode(EmailSendDTO dto);

    VerificationResult verify(EmailVerifyDTO dto, HttpServletResponse response);

    record VerificationResult(UserLoginResponseVO response, String trustedDeviceToken, Integer userId) {
    }
}
