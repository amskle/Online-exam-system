package com.example.onlineexamsystem.utils;

/**
 * 用户上下文工具（基于 ThreadLocal）
 */
public class UserContext {
    /**
     * 用户信息
     */
    public record UserInfo(Integer userId, Integer role) {
    }

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 设置用户信息
     *
     * @param userId 用户ID
     * @param role   用户角色
     */
    public static void setUser(Integer userId, Integer role) {
        CONTEXT.set(new UserInfo(userId, role));
    }

    /**
     * 获取用户信息
     *
     * @return UserInfo
     */
    public static UserInfo getUser() {
        return CONTEXT.get();
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public static Integer getUserId() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.userId : null;
    }

    /**
     * 获取角色
     *
     * @return 角色值
     */
    public static Integer getRole() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.role : null;
    }

    /**
     * 检查用户是否已登录
     *
     * @return 是否已登录
     */
    public static boolean isLoggedIn() {
        return CONTEXT.get() != null;
    }

    /**
     * 清除用户上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
