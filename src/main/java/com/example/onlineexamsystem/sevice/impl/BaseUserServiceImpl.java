package com.example.onlineexamsystem.sevice.impl;

import com.example.onlineexamsystem.mapper.BaseUserMapper;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.sevice.BaseUserService;
import org.springframework.stereotype.Service;

/*
 * 基础用户服务实现类
 */
@Service
public class BaseUserServiceImpl extends ServiceImpl<BaseUserMapper, BaseUser> implements BaseUserService {

    /**
     * 登录
     * @param userLoginDTO
     */
    @Override
    public void login(UserLoginDTO userLoginDTO) {

    }
}
