package com.example.onlineexamsystem.controller;


import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.sevice.BaseUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 基础用户控制器
 */
@RestController
@RequestMapping("/admin")
public class BaseUserController {

    @Autowired
    private BaseUserService baseUserService;
    @PostMapping("/login")
    private String login(@RequestBody UserLoginDTO userLoginDTO) {
        baseUserService.login(userLoginDTO);
        return "Hello World";
    }
}
