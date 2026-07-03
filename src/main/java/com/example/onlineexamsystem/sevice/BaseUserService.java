package com.example.onlineexamsystem.sevice;

/*
 * 基础用户服务接口
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import org.springframework.stereotype.Service;

@Service
public interface BaseUserService extends IService<BaseUser> {

    void login(UserLoginDTO userLoginDTO);

    void register(UserRegisterDTO userRegisterDTO);
}
