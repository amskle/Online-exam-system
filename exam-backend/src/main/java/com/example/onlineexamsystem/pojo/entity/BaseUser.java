package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author 基础用户信息
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("user")
public class BaseUser {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键id
    private String account; // 账号
    private String password; // 密码
    private String avatar; // 头像
    private String username; // 用户名
    private Integer gender; // 性别(1.男，2.女)
    private String phone; // 电话
    private String email; // 邮箱
    private LocalDateTime emailVerifyTime; // 邮箱验证时间
    private Boolean loginStatus; // 登录状态(0.正常，1.封号)
    private Integer role; // 角色(1.学生，2.教师，3.管理员)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime; // 创建时间
}
