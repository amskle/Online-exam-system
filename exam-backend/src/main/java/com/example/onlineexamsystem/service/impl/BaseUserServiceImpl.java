package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.common.exception.BusinessException;
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
import com.example.onlineexamsystem.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 基础用户服务实现类
 */
@Service
@AllArgsConstructor
public class BaseUserServiceImpl extends ServiceImpl<BaseUserMapper, BaseUser> implements BaseUserService {
    private final JwtUtil jwtUtil;
    private final FileUploadService fileUploadService;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    /**
     * 登录
     *
     * @param userLoginDTO 登录参数对象
     */
    @Override
    public UserLoginResponseVO login(UserLoginDTO userLoginDTO) {
        String failKey = "login_fail:" + userLoginDTO.getAccount();

        // ① 检查是否已被锁定
        String failCountStr = redisUtil.get(failKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= 5) {
            long remainingSeconds = redisUtil.getExpireSeconds(failKey);
            throw new BusinessException(
                    "账号已被锁定，请 " + (remainingSeconds / 60 + 1) + " 分钟后重试");
        }

        // ② 查询用户（原有逻辑）
        BaseUser baseUser = this.getOne(
                new LambdaQueryWrapper<BaseUser>()
                        .eq(BaseUser::getAccount, userLoginDTO.getAccount())
        );
        if (baseUser == null) {
            recordFailure(failKey);  // 账号不存在也算一次失败
            throw new BusinessException("账号或密码错误");
        }
        if (Boolean.TRUE.equals(baseUser.getLoginStatus())) {
            throw new BusinessException("账号已被停用，请联系管理员");
        }

        // ③ 密码判断
        boolean passwordMatched = baseUser.getPassword().startsWith("$2")
                ? passwordEncoder.matches(userLoginDTO.getPassword(), baseUser.getPassword())
                : Objects.equals(baseUser.getPassword(), userLoginDTO.getPassword());
        if (!passwordMatched) {
            recordFailure(failKey);
            throw new BusinessException("密码错误");
        }

        // ④ 登录成功 → 清除失败计数
        redisUtil.delete(failKey);

        // 生成登录版本号（顶号用），存储到 Redis 并写入 JWT
        String loginVersion = UUID.randomUUID().toString();
        redisUtil.put("user:login_version:" + baseUser.getId(), loginVersion, Duration.ofDays(7));
        String token = jwtUtil.generateToken(baseUser.getId(), baseUser.getRole(), loginVersion);
        return UserLoginResponseVO
                .builder()
                .status("AUTHENTICATED")
                .userId(baseUser.getId())
                .roleName(RoleEnum.getByRole(baseUser.getRole()).getDescription())
                .build();
    }

    private void recordFailure(String failKey) {
        long count = redisUtil.recordLoginFailure(failKey, Duration.ofMinutes(15), 5);
        if (count >= 5) {
            throw new BusinessException("密码错误次数过多，账号已被锁定15分钟");
        }
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
                .password(passwordEncoder.encode(userRegisterDTO.getPassword()))
                .username(userRegisterDTO.getUsername())
                .role(userRegisterDTO.getRole())
                .email(userRegisterDTO.getEmail())
                .emailVerifyTime(LocalDateTime.now())
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
                .email(baseUser.getEmail())
                .emailVerifyTime(baseUser.getEmailVerifyTime())
                .loginStatus(baseUser.getLoginStatus())
                .role(baseUser.getRole())
                .build();
    }

    /**
     * 修改密码
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
                    .password(passwordEncoder.encode(userUpdatePasswordDTO.getPassword()))
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

    /**
     * 修改头像（切换头像时删除旧文件）
     *
     * @param baseUserUpdateDTO 修改个人信息参数对象
     */
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
