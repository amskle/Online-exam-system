package com.example.onlineexamsystem.interceptor;

import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.api.ResultCode;
import com.example.onlineexamsystem.utils.JwtUtil;
import com.example.onlineexamsystem.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * Jwt拦截器
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    // 不需要拦截的路径
    private static final String[] EXCLUDE_PATHS = {
            "/login",
            "/register",
            "/files/upload"
    };

    /**
     * 请求前置拦截：验证 Token 与角色权限
     *
     * @return boolean 是否放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String path = request.getRequestURI();
        // 检查是否在白名单中
        if (isExcludePath(path)) {
            return true;
        }
        // 如果不是方法级别的映射，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        // 获取权限注解（优先方法级，其次类级）
        Auth auth = getAuthAnnotation(method);
        // 没有 @Auth 注解，说明不需要登录，直接放行
        if (auth == null) {
            return true;
        }

        //从 Header 中获取 token
        String token = request.getHeader("Authorization");

        if (token == null || token.trim().isEmpty()) {
            handleUnauthorized(response, "未提供认证令牌");
            return false;
        }

        // 去除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            // 验证 token
            Claims claims = jwtUtil.getClaims(token);
            if (claims == null) {
                throw new BusinessException("请先登录...");
            }
            // 解析用户信息
            Integer userId = jwtUtil.getUserId(token);
            if (userId == null || userId == 0) {
                throw new BusinessException("token中无用户信息");
            }
            Integer role = jwtUtil.getRole(token);
            // 将用户信息存储到 ThreadLocal
            UserContext.setUser(userId, role);
            int[] requiredRoles = auth.value();
            // 如果注解中指定了角色，则需要校验角色
            if (requiredRoles.length > 0) {
                Integer currentRole = UserContext.getRole();
                if (currentRole == null || !hasRequiredRole(currentRole, requiredRoles)) {
                    handleForbidden(response, "无权限访问");
                    return false;
                }
            }

            // 有 @Auth 注解且没有指定角色，只需要登录即可，已经登录成功，放行
            return true;

        } catch (Exception e) {
            handleUnauthorized(response, "认证失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 获取方法或类上的 @Auth 注解
     */
    private Auth getAuthAnnotation(Method method) {
        // 优先检查方法级别
        if (method.isAnnotationPresent(Auth.class)) {
            return method.getAnnotation(Auth.class);
        }
        // 检查类级别
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass.isAnnotationPresent(Auth.class)) {
            return declaringClass.getAnnotation(Auth.class);
        }
        return null;
    }

    /**
     * 检查当前用户是否拥有所需角色
     */
    private boolean hasRequiredRole(Integer currentRole, int[] requiredRoles) {
        for (int role : requiredRoles) {
            if (role == currentRole) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理未授权请求（未登录）
     */
    private void handleUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json; charset=utf-8");
        Result<String> fail = Result.fail(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(fail));
    }

    /**
     * 处理禁止访问请求（已登录但权限不足）
     */
    private void handleForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json; charset=utf-8");
        Result<String> fail = Result.fail(ResultCode.FORBIDDEN, message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(fail));
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isExcludePath(String path) {
        for (String excludePath : EXCLUDE_PATHS) {
            if (path.contains(excludePath)) {
                return true;
            }
        }
        return false;
    }
}
