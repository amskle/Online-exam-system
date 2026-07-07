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

@Service
public interface BaseUserService extends IService<BaseUser> {

    void updatePassword(Integer id, UserUpdatePasswordDTO userUpdatePasswordDTO);

    UserLoginResponseVO login(UserLoginDTO userLoginDTO);

    void register(UserRegisterDTO userRegisterDTO);

    BaseUserVO tokenAuth(String token);

    void updateInfo(@Valid BaseUserUpdateDTO baseUserUpdateDTO);

    void updateAvatar(@Valid BaseUserUpdateDTO baseUserUpdateDTO);
}
