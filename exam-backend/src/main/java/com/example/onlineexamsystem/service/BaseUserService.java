package com.example.onlineexamsystem.service;

/*
 * 基础用户服务接口
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.vo.BaseUserVO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import org.springframework.stereotype.Service;

@Service
public interface BaseUserService extends IService<BaseUser> {

    UserLoginResponseVO login(UserLoginDTO userLoginDTO);

    void register(UserRegisterDTO userRegisterDTO);

    BaseUserVO tokenAuth(String token);
}
