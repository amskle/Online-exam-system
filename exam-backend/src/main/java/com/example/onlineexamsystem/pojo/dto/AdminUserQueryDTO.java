package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员用户查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends PageQueryDTO {
    private String account;
    private String username;
    private String phone;
    private Boolean loginStatus;
    private Integer role;
}
