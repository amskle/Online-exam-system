package com.example.onlineexamsystem.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 — 密钥通过 ${jwt.secret} 从配置/环境变量注入
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    private static final String CLAIM_LOGIN_VERSION = "loginVer";

    /**
     * 生成 JWT Token（不带登录版本号，兼容旧调用）
     *
     * @param userId 用户ID
     * @param role   用户角色
     * @return Token
     */
    public String generateToken(Integer userId, Integer role) {
        return generateToken(userId, role, null);
    }

    /**
     * 生成 JWT Token（带登录版本号，用于顶号检测）
     *
     * @param userId       用户ID
     * @param role         用户角色
     * @param loginVersion 登录版本号，null 则不写入
     * @return Token
     */
    public String generateToken(Integer userId, Integer role, String loginVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (loginVersion != null) {
            claims.put(CLAIM_LOGIN_VERSION, loginVersion);
        }
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从 Token 中获取登录版本号
     *
     * @param token Token
     * @return 登录版本号，不存在则返回 null
     */
    public String getLoginVersion(String token) {
        return getClaims(token).get(CLAIM_LOGIN_VERSION, String.class);
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取用户ID
     *
     * @param token Token
     * @return 用户ID
     */
    public Integer getUserId(String token) {
        return Integer.valueOf(getClaims(token).getSubject());
    }

    /**
     * 从 Token 中获取角色
     *
     * @param token Token
     * @return 角色值
     */
    public Integer getRole(String token) {
        return getClaims(token).get("role", Integer.class);
    }

    /**
     * 解析 Token 获取 Claims
     *
     * @param token Token
     * @return Claims
     */
    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
