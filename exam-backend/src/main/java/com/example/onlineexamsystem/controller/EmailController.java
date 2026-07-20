package com.example.onlineexamsystem.controller;

import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.EmailSendDTO;
import com.example.onlineexamsystem.pojo.dto.EmailVerifyDTO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import com.example.onlineexamsystem.service.EmailService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController {
    public static final String TRUSTED_DEVICE_COOKIE_PREFIX = "trusted_device_";
    private static final String LEGACY_COOKIE = "trusted_device";

    private final EmailService emailService;

    @Value("${auth.trusted-device-ttl:7d}")
    private Duration trustedDeviceTtl;

    @Value("${auth.trusted-device-secure-cookie:false}")
    private boolean secureCookie;

    @PostMapping("/send")
    public Result<UserLoginResponseVO> send(@Valid @RequestBody EmailSendDTO dto) {
        return Result.success(emailService.sendCode(dto));
    }

    @PostMapping("/verify")
    public Result<UserLoginResponseVO> verify(
            @Valid @RequestBody EmailVerifyDTO dto,
            HttpServletResponse servletResponse) {
        EmailService.VerificationResult result = emailService.verify(dto);
        if (result.trustedDeviceToken() != null && result.userId() != null) {
            String cookieName = TRUSTED_DEVICE_COOKIE_PREFIX + result.userId();
            ResponseCookie cookie = ResponseCookie.from(cookieName, result.trustedDeviceToken())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(trustedDeviceTtl)
                    .build();
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            // 清除旧版共用 Cookie，实现向后兼容迁移
            ResponseCookie legacyExpire = ResponseCookie.from(LEGACY_COOKIE, "")
                    .httpOnly(true)
                    .secure(secureCookie)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(0)
                    .build();
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, legacyExpire.toString());
        }
        return Result.success(result.response());
    }
}
