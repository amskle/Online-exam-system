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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    private final BaseUserService baseUserService;
    private final EmailService emailService;

    /**
     * 用户登录
     *
     * @return Result<UserLoginResponseVO>
     */
    @PostMapping("/login")
    public Result<UserLoginResponseVO> login(
            @Valid @RequestBody UserLoginDTO userLoginDTO,
            HttpServletRequest request) {
        Map<Integer, String> trustedDeviceTokens = extractTrustedDeviceTokens(request);
        UserLoginResponseVO userLoginResponseVO = emailService.beginLogin(userLoginDTO, trustedDeviceTokens);
        return Result.success(userLoginResponseVO);
    }

    /**
     * token认证
     *
     * @return Result<BaseUserVO>
     */
    @GetMapping("/{token}/auth")
    public Result<BaseUserVO> tokenAuth(@PathVariable String token) {
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
