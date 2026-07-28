package com.example.onlineexamsystem.interceptor;

import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.api.ResultCode;
import com.example.onlineexamsystem.utils.JwtUtil;
import com.example.onlineexamsystem.utils.RedisUtil;
import com.example.onlineexamsystem.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * Jwt拦截器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    // 不需要拦截的路径（使用 startsWith 精确匹配路径前缀）
    private static final String[] EXCLUDE_PATHS = {
            "/user/login",
            "/user/register",
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
        log.info("拦截请求：{}", path);
        // 检查是否在白名单中
        if (isExcludePath(path)) {
            log.info("白名单放行：{}", path);
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

        //从 Header 中获取 token（优先），其次从 HttpOnly Cookie 获取
        String token = request.getHeader("Authorization");

        if (token == null || token.trim().isEmpty()) {
            // 回退到 Cookie 中查找
            token = extractTokenFromCookie(request);
        }

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

            // 顶号检测：验证 JWT 中的登录版本号是否与 Redis 一致
            String loginVersion = jwtUtil.getLoginVersion(token);
            if (loginVersion != null) {
                String storedVersion = redisUtil.get("user:login_version:" + userId);
                if (storedVersion != null && !storedVersion.equals(loginVersion)) {
                    handleUnauthorized(response, "账号已在其他设备登录，请重新登录");
                    return false;
                }
                // storedVersion 为 null（Redis 不可用或 key 过期）时放行，避免误伤
            }

            int[] requiredRoles = auth.value();
            // 如果注解中指定了角色，则需要校验角色
            if (requiredRoles.length > 0) {
                Integer currentRole = UserContext.getRole();
                if (currentRole == null || !hasRequiredRole(currentRole, requiredRoles)) {
                    handleForbidden(response, "无权限访问");
                    return false;
                }
            }
            UserContext.setUser(userId, role);
            log.info("用户上下文已设置：userId：{}, role：{}", userId, role);

            log.info("鉴权通过：{} (userId={}, role={})", path, userId, role);

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
     * 从 HttpOnly Cookie 中提取 Token（回退方案）
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if ("exam_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 检查路径是否在白名单中（使用 startsWith 精确匹配路径前缀）
     */
    private boolean isExcludePath(String path) {
        for (String excludePath : EXCLUDE_PATHS) {
            if (path.startsWith(excludePath)) {
                return true;
            }
        }
        return false;
    }
}
