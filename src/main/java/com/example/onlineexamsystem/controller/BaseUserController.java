package com.example.onlineexamsystem.controller;


import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.sevice.BaseUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * 基础用户控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class BaseUserController {

    private final BaseUserService baseUserService;

    /**
     * 用户登录
     * @return String
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        baseUserService.login(userLoginDTO);
        return Result.success();
    }

    /**
     * 用户注册
     * @return String
     */
    @PostMapping("/register")
    public String register(@RequestBody UserRegisterDTO userRegisterDTO) {
        baseUserService.register(userRegisterDTO);
        return "register success";
    }
}
