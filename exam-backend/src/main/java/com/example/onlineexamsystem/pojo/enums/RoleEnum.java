package com.example.onlineexamsystem.pojo.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 角色枚举类
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    STUDENT(1, "学生"),
    TEACHER(2, "教师"),
    ADMIN(3, "管理员");
    private final Integer role;
    private final String description;

    public static RoleEnum getByRole(Integer role) {
        for (RoleEnum r : values()) {
            if (r.role.equals(role)) {
                return r;
            }
        }
        throw new IllegalArgumentException("未知的角色值: " + role);
    }
}
