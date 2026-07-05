package com.example.onlineexamsystem.controller;


import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.dto.UserUpdatePasswordDTO;
import com.example.onlineexamsystem.pojo.vo.BaseUserVO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import com.example.onlineexamsystem.service.BaseUserService;
import jakarta.validation.Valid;
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
     *
     * @return UserLoginResponseVO
     */
    @PostMapping("/login")
    public Result<UserLoginResponseVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        UserLoginResponseVO userLoginResponseVO = baseUserService.login(userLoginDTO);
        return Result.success(userLoginResponseVO);
    }

    /**
     * token认证
     *
     * @return BaseUserVO
     */
    @GetMapping("/{token}/{auth}")
    public Result<BaseUserVO> tokenAuth(@PathVariable String token) {
        BaseUserVO baseUserVO = baseUserService.tokenAuth(token);
        return Result.success(baseUserVO);
    }

    /**
     * 用户注册
     *
     * @return String
     */
    @PostMapping("/register")
    public String register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        baseUserService.register(userRegisterDTO);
        return "register success";
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
}
