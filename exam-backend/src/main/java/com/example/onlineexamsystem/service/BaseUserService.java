package com.example.onlineexamsystem.service;

/*
 * 基础用户服务接口
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.BaseUserUpdateDTO;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.dto.UserUpdatePasswordDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.vo.BaseUserVO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

/**
 * 基础用户服务接口
 */
@Service
public interface BaseUserService extends IService<BaseUser> {

    /**
     * 修改密码
     *
     * @param id                    用户id
     * @param userUpdatePasswordDTO 修改密码参数对象
     */
    void updatePassword(Integer id, UserUpdatePasswordDTO userUpdatePasswordDTO);

    /**
     * 用户登录
     *
     * @param userLoginDTO 登录参数对象
     * @return UserLoginResponseVO
     */
    UserLoginResponseVO login(UserLoginDTO userLoginDTO);

    /**
     * 用户注册
     *
     * @param userRegisterDTO 注册参数对象
     */
    void register(UserRegisterDTO userRegisterDTO);

    /**
     * 通过token获取用户信息
     *
     * @param token 令牌
     * @return BaseUserVO
     */
    BaseUserVO tokenAuth(String token);

    /**
     * 修改个人信息
     *
     * @param baseUserUpdateDTO 修改个人信息参数对象
     */
    void updateInfo(@Valid BaseUserUpdateDTO baseUserUpdateDTO);

    /**
     * 修改头像
     *
     * @param baseUserUpdateDTO 修改个人信息参数对象
     */
    void updateAvatar(@Valid BaseUserUpdateDTO baseUserUpdateDTO);
}
