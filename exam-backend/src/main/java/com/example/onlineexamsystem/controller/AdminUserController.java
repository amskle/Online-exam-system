package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.AdminUserQueryDTO;
import com.example.onlineexamsystem.pojo.dto.AdminUserSaveDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.BaseUserService;
import com.example.onlineexamsystem.utils.RedisUtil;
import com.example.onlineexamsystem.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

/**
 * 管理员用户控制器
 */
/**
 * 管理员用户控制器
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Auth(3)
public class AdminUserController {
    private final BaseUserService baseUserService;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    /**
     * 分页查询用户列表
     *
     * @return Result<PageVO<BaseUser>>
     */
    /**
     * 分页查询用户列表
     *
     * @return Result<PageVO<BaseUser>>
     */
    @GetMapping("/listPage")
    public Result<PageVO<BaseUser>> listPage(AdminUserQueryDTO query) {
        Page<BaseUser> page = baseUserService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<BaseUser>()
                        .in(query.getRole() != null && query.getRole() == 1, BaseUser::getRole, 1, 2)
                        .eq(query.getRole() != null && query.getRole() != 1, BaseUser::getRole, query.getRole())
                        .like(StringUtils.hasText(query.getAccount()), BaseUser::getAccount, query.getAccount())
                        .like(StringUtils.hasText(query.getUsername()), BaseUser::getUsername, query.getUsername())
                        .like(StringUtils.hasText(query.getPhone()), BaseUser::getPhone, query.getPhone())
                        .eq(query.getLoginStatus() != null, BaseUser::getLoginStatus, query.getLoginStatus())
                        .orderByDesc(BaseUser::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 查询用户详情
     *
     * @return Result<BaseUser>
     */
    /**
     * 获取用户详情
     *
     * @return Result<BaseUser>
     */
    @GetMapping("/{id}")
    public Result<BaseUser> detail(@PathVariable Integer id) {
        return Result.success(baseUserService.getById(id));
    }

    /**
     * 新增用户
     *
     * @return Result<Void>
     */
    /**
     * 新增用户
     *
     * @return Result<Void>
     */
    @PostMapping
    public Result<Void> add(@Valid @RequestBody AdminUserSaveDTO dto) {
        BaseUser existed = baseUserService.getOne(new LambdaQueryWrapper<BaseUser>().eq(BaseUser::getAccount, dto.getAccount()));
        if (existed != null) {
            throw new BusinessException("账号不可用");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        BaseUser user = new BaseUser();
        BeanUtils.copyProperties(dto, user);
        user.setId(null);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setLoginStatus(dto.getLoginStatus() != null && dto.getLoginStatus());
        user.setCreateTime(LocalDateTime.now());
        baseUserService.save(user);
        return Result.success();
    }

    /**
     * 修改用户
     *
     * @return Result<Void>
     */
    /**
     * 更新用户信息
     *
     * @return Result<Void>
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AdminUserSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("用户id不能为空");
        }
        BaseUser existing = baseUserService.getById(dto.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getId().equals(UserContext.getUserId())
                && (!Integer.valueOf(3).equals(dto.getRole()) || Boolean.TRUE.equals(dto.getLoginStatus()))) {
            throw new BusinessException("不能停用当前账号或移除自己的管理员权限");
        }
        BaseUser sameAccount = baseUserService.getOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getAccount, dto.getAccount())
                .ne(BaseUser::getId, dto.getId()));
        if (sameAccount != null) {
            throw new BusinessException("账号不可用");
        }
        BaseUser user = new BaseUser();
        BeanUtils.copyProperties(dto, user);
        if (!StringUtils.hasText(dto.getPassword())) {
            user.setPassword(null);
        } else {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        baseUserService.updateById(user);
        if (!existing.getRole().equals(dto.getRole()) || StringUtils.hasText(dto.getPassword())) {
            invalidateActiveTokens(dto.getId());
        }
        return Result.success();
    }

    /**
     * 修改用户登录状态
     *
     * @return Result<Void>
     */
    /**
     * 更新用户登录状态（启用/禁用）
     *
     * @return Result<Void>
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestParam Boolean loginStatus) {
        if (id.equals(UserContext.getUserId()) && Boolean.TRUE.equals(loginStatus)) {
            throw new BusinessException("不能停用当前登录账号");
        }
        BaseUser user = BaseUser.builder().id(id).loginStatus(loginStatus).build();
        if (!baseUserService.updateById(user)) {
            throw new BusinessException("用户不存在");
        }
        if (Boolean.TRUE.equals(loginStatus)) {
            invalidateActiveTokens(id);
        }
        return Result.success();
    }

    /**
     * 删除用户
     *
     * @return Result<Void>
     */
    /**
     * 删除用户
     *
     * @return Result<Void>
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        if (id.equals(UserContext.getUserId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        if (!baseUserService.removeById(id)) {
            throw new BusinessException("用户不存在");
        }
        invalidateActiveTokens(id);
        return Result.success();
    }

    private void invalidateActiveTokens(Integer userId) {
        redisUtil.put("user:login_version:" + userId, UUID.randomUUID().toString(), Duration.ofDays(7));
    }
}
