package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.AdminUserQueryDTO;
import com.example.onlineexamsystem.pojo.dto.AdminUserSaveDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.BaseUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Auth(3)
public class AdminUserController {
    private final BaseUserService baseUserService;

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

    @GetMapping("/{id}")
    public Result<BaseUser> detail(@PathVariable Integer id) {
        return Result.success(baseUserService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody AdminUserSaveDTO dto) {
        BaseUser existed = baseUserService.getOne(new LambdaQueryWrapper<BaseUser>().eq(BaseUser::getAccount, dto.getAccount()));
        if (existed != null) {
            throw new BusinessException("账号不可用");
        }
        BaseUser user = new BaseUser();
        BeanUtils.copyProperties(dto, user);
        user.setId(null);
        user.setLoginStatus(dto.getLoginStatus() == null || dto.getLoginStatus());
        user.setCreateTime(LocalDateTime.now());
        baseUserService.save(user);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody AdminUserSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("用户id不能为空");
        }
        BaseUser user = new BaseUser();
        BeanUtils.copyProperties(dto, user);
        if (!StringUtils.hasText(dto.getPassword())) {
            user.setPassword(null);
        }
        baseUserService.updateById(user);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestParam Boolean loginStatus) {
        BaseUser user = BaseUser.builder().id(id).loginStatus(loginStatus).build();
        baseUserService.updateById(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        baseUserService.removeById(id);
        return Result.success();
    }
}
