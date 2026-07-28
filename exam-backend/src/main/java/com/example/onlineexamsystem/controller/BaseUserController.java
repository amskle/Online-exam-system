package com.example.onlineexamsystem.controller;


import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.BaseUserUpdateDTO;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.dto.UserUpdatePasswordDTO;
import com.example.onlineexamsystem.pojo.vo.BaseUserVO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import com.example.onlineexamsystem.service.BaseUserService;
import com.example.onlineexamsystem.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


/**
 * 基础用户控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class BaseUserController {

    private static final String COOKIE_PREFIX = EmailController.TRUSTED_DEVICE_COOKIE_PREFIX;
    private static final String AUTH_COOKIE_NAME = "exam_token";

    private final BaseUserService baseUserService;
    private final EmailService emailService;

    @Value("${auth.trusted-device-ttl:7d}")
    private Duration trustedDeviceTtl;

    @Value("${auth.trusted-device-secure-cookie:false}")
    private boolean secureCookie;

    /**
     * 用户登录
     *
     * @return Result<UserLoginResponseVO>
     */
    @PostMapping("/login")
    public Result<UserLoginResponseVO> login(
            @Valid @RequestBody UserLoginDTO userLoginDTO,
            HttpServletRequest request,
            HttpServletResponse response) {
        Map<Integer, String> trustedDeviceTokens = extractTrustedDeviceTokens(request);
        UserLoginResponseVO userLoginResponseVO = emailService.beginLogin(userLoginDTO, trustedDeviceTokens, response);
        return Result.success(userLoginResponseVO);
    }

    /**
     * 用户登出 — 清除认证 Cookie
     *
     * @return Result<Void>
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return Result.success();
    }

    /**
     * token认证 — 从 Authorization 请求头或 HttpOnly Cookie 中获取 Token
     *
     * @return Result<BaseUserVO>
     */
    @GetMapping("/auth")
    public Result<BaseUserVO> tokenAuth(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            // 回退到 HttpOnly Cookie
            token = extractTokenFromCookie(request);
        }
        BaseUserVO baseUserVO = baseUserService.tokenAuth(token);
        return Result.success(baseUserVO);
    }

    /**
     * 用户注册
     *
     * @return Result<String>
     */
    @PostMapping("/register")
    public Result<UserLoginResponseVO> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        return Result.success(emailService.beginRegister(userRegisterDTO));
    }

    /**
     * 修改密码
     *
     * @return Result<Void>
     */
    @PutMapping("/{id}/updatePassword")
    private Result<Void> updatePassword(
            @PathVariable Integer id,
            @Valid @RequestBody UserUpdatePasswordDTO userUpdatePasswordDTO) {
        baseUserService.updatePassword(id, userUpdatePasswordDTO);
        return Result.success();
    }

    /**
     * 修改个人信息
     *
     * @return Result<UserLoginResponseVO>
     */
    @PutMapping
    public Result<Void> updateInfo(@Valid @RequestBody BaseUserUpdateDTO baseUserUpdateDTO) {
        baseUserService.updateInfo(baseUserUpdateDTO);
        return Result.success();
    }

    /**
     * 上传头像
     *
     * @return Result<UserLoginResponseVO>
     */
    @PutMapping(value = "/uploadAvatar")
    public Result<Void> uploadAvatar(@Valid @RequestBody BaseUserUpdateDTO baseUserUpdateDTO) {
        baseUserService.updateAvatar(baseUserUpdateDTO);
        return Result.success();
    }

    /**
     * 从 HttpOnly Cookie 中提取认证 Token
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Map<Integer, String> extractTrustedDeviceTokens(HttpServletRequest request) {
        Map<Integer, String> tokens = new HashMap<>();
        if (request.getCookies() == null) {
            return tokens;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            String name = cookie.getName();
            if (name != null && name.startsWith(COOKIE_PREFIX)) {
                try {
                    Integer userId = Integer.valueOf(name.substring(COOKIE_PREFIX.length()));
                    tokens.put(userId, cookie.getValue());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return tokens;
    }
}
