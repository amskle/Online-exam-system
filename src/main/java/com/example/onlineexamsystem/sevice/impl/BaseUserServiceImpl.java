package com.example.onlineexamsystem.sevice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.exception.ValidationException;
import com.example.onlineexamsystem.mapper.BaseUserMapper;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.pojo.enums.AccountStatusEnum;
import com.example.onlineexamsystem.sevice.BaseUserService;
import com.fasterxml.jackson.databind.ser.Serializers;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.builder.BuilderException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Objects;

/*
 * 基础用户服务实现类
 */
@Service
public class BaseUserServiceImpl extends ServiceImpl<BaseUserMapper, BaseUser> implements BaseUserService {

    /**
     * 登录
     * @param userLoginDTO 登录参数对象
     */
    @Override
    public void login(UserLoginDTO userLoginDTO) {
        if (StringUtils.isEmpty(userLoginDTO.getAccount())) {
            throw new ValidationException("账号不能为空");
        }
        if (StringUtils.isEmpty(userLoginDTO.getPassword())) {
            throw new ValidationException("密码不能为空");
        }

        BaseUser baseUser = this.getOne(
                new LambdaQueryWrapper<BaseUser>()
                        .eq(BaseUser::getAccount, userLoginDTO.getAccount())
        );
        if (baseUser == null) {
            throw new BusinessException("账号不存在");
        }
        if (!Objects.equals(baseUser.getPassword(), userLoginDTO.getPassword())) {
            throw new BusinessException("密码错误");
        }
    }

    /**
     * 注册
     * @param userRegisterDTO 注册参数对象
     */
    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
        if (StringUtils.isEmpty(userRegisterDTO.getAccount())) {
            throw new ValidationException("账号不能为空");
        }
        if (StringUtils.isEmpty(userRegisterDTO.getPassword())) {
            throw new ValidationException("密码不能为空");
        }
        if (StringUtils.isEmpty(userRegisterDTO.getUsername())) {
            throw new ValidationException("用户名不能为空");
        }
        BaseUser baseUser = this.getOne(
                new LambdaQueryWrapper<BaseUser>()
                        .eq(BaseUser::getAccount, userRegisterDTO.getAccount())
        );
        if (Objects.nonNull(baseUser)) {
            throw new BusinessException("账号不可用");
        }
        BaseUser baseUserSave = BaseUser.builder()
                .account(userRegisterDTO.getAccount())
                .password(userRegisterDTO.getPassword())
                .username(userRegisterDTO.getUsername())
                .loginStatus(AccountStatusEnum.NORMAL.getStatusCode())
                .createTime(LocalDate.now())
                .build();
        this.save(baseUserSave);
    }
}
