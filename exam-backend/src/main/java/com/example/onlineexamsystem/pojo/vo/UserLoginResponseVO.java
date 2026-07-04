package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录响应VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginResponseVO {
    private String token;
    private Integer role;
    private String roleName;
}
