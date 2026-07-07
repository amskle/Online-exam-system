package com.example.onlineexamsystem.annotation;

import java.lang.annotation.*;

/**
 * 权限注解 —— 直接使用数据库 role 值（tinyint）
 * 使用示例：
 * - @Auth           // 只需要登录
 * - @Auth(3)        // 仅管理员
 * - @Auth({1, 2})   // 学生或教师
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented

public @interface Auth {

    /**
     * 允许的角色列表（对应数据库 role: 1=学生, 2=教师, 3=管理员），为空时只需要登录
     */
    int[] value() default {};
}
