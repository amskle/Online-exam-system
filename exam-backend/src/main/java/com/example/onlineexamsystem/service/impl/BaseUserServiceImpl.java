package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.mapper.BaseUserMapper;
import com.example.onlineexamsystem.pojo.dto.BaseUserUpdateDTO;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.dto.UserUpdatePasswordDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.enums.AccountStatusEnum;
import com.example.onlineexamsystem.pojo.enums.RoleEnum;
import com.example.onlineexamsystem.pojo.vo.BaseUserVO;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import com.example.onlineexamsystem.service.BaseUserService;
import com.example.onlineexamsystem.service.FileUploadService;
import com.example.onlineexamsystem.utils.JwtUtil;
import com.fasterxml.jackson.databind.util.BeanUtil;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/*
 * 基础用户服务实现类
 */
@Service
@AllArgsConstructor
public class BaseUserServiceImpl extends ServiceImpl<BaseUserMapper, BaseUser> implements BaseUserService {
    private final JwtUtil jwtUtil;
    private final FileUploadService fileUploadService;


    /**
     * 登录
     *
     * @param userLoginDTO 登录参数对象
     */
    @Override
    public UserLoginResponseVO login(UserLoginDTO userLoginDTO) {
        // 通过账号查询账户信息
        BaseUser baseUser = this.getOne(
                new LambdaQueryWrapper<BaseUser>()
                        .eq(BaseUser::getAccount, userLoginDTO.getAccount())
        );
        if (baseUser == null) {
            throw new BusinessException("账号不存在");
        }
        // 密码判断
        if (!Objects.equals(baseUser.getPassword(), userLoginDTO.getPassword())) {
            throw new BusinessException("密码错误");
        }
        if (Boolean.TRUE.equals(baseUser.getLoginStatus())) {
            throw new BusinessException("账号已被停用，请联系管理员");
        }
        // 生成token
        String token = jwtUtil.generateToken(baseUser.getId(), baseUser.getRole());
        return UserLoginResponseVO
                .builder()
                .token(token)
                .role(baseUser.getRole())
                .roleName(RoleEnum.getByRole(baseUser.getRole()).getDescription())
                .build();
    }

    /**
     * 注册
     *
     * @param userRegisterDTO 注册参数对象
     */
    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
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
                .role(userRegisterDTO.getRole() != null ? userRegisterDTO.getRole() : 1)
                .loginStatus(AccountStatusEnum.NORMAL.getStatusCode())
                .createTime(LocalDateTime.now())
                .build();
        this.save(baseUserSave);
    }

    /**
     * 通过token获取用户信息
     *
     * @param token 令牌
     * @return BaseUserVO
     */
    @Override
    public BaseUserVO tokenAuth(String token) {
        Claims claims = jwtUtil.getClaims(token);
        if (claims == null) {
            throw new BusinessException("token异常");
        }
        Integer userIdStr = jwtUtil.getUserId(token);
        if (userIdStr == null || userIdStr == 0) {
            throw new BusinessException("token中无用户信息");
        }
        int userId = userIdStr;
        BaseUser baseUser = this.getById(userId);
        return BaseUserVO.builder()
                .id(baseUser.getId())
                .username(baseUser.getUsername())
                .account(baseUser.getAccount())
                .avatar(baseUser.getAvatar())
                .gender(baseUser.getGender())
                .phone(baseUser.getPhone())
                .loginStatus(baseUser.getLoginStatus())
                .role(baseUser.getRole())
                .build();
    }

    /**
     * 修改密码
     * TODO 权限认证
     *
     * @param id                    用户id
     * @param userUpdatePasswordDTO 修改密码参数对象
     */
    @Override
    public void updatePassword(Integer id, UserUpdatePasswordDTO userUpdatePasswordDTO) {
        if (Objects.nonNull(id)) {
            BaseUser baseUser = this.getById(id);
            if (Objects.isNull(baseUser)) {
                throw new BusinessException("用户信息查询异常");
            }
            BaseUser buildUserEntity = BaseUser.builder()
                    .id(id)
                    .password(userUpdatePasswordDTO.getPassword())
                    .build();
            this.updateById(buildUserEntity);
        }
    }

    /**
     * 修改个人信息
     *
     * @param baseUserUpdateDTO 修改个人信息参数对象
     */
    @Override
    public void updateInfo(BaseUserUpdateDTO baseUserUpdateDTO) {
        BaseUser baseUserUpdate = this.getById(baseUserUpdateDTO.getId());
        if (Objects.isNull(baseUserUpdate)) {
            throw new BusinessException("用户信息查询异常");
        }
        BaseUser baseUser = new BaseUser();
        BeanUtils.copyProperties(baseUserUpdateDTO, baseUser);
        this.updateById(baseUser);
    }

    @Override
    public void updateAvatar(BaseUserUpdateDTO baseUserUpdateDTO) {
        BaseUser user = this.getById(baseUserUpdateDTO.getId());

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 删除旧头像（如果存在且不是同一个）
        if (user.getAvatar() != null
                && !user.getAvatar().isBlank()
                && !user.getAvatar().equals(baseUserUpdateDTO.getAvatar())) {

            fileUploadService.deleteFile(user.getAvatar());
        }

        // 更新数据库
        user.setAvatar(baseUserUpdateDTO.getAvatar());

        this.updateById(user);
    }
}
