package com.example.onlineexamsystem.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    private static final String SECRET_KEY = "t8Kx9mN2vB5qW3pL7sF4cH6jU1yR8eZ0aD5gJ9nM2xP4sV7wC3"; // 请替换为更安全的密钥
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;

    private Key getKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    public String generateToken(Integer userId, Integer role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 验证 token
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 获取用户ID
    public Integer getUserId(String token) {
        return Integer.valueOf(getClaims(token).getSubject());
    }

    // 获取角色
    public Integer getRole(String token) {
        return getClaims(token).get("role", Integer.class);
    }

    // 解析token
    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}