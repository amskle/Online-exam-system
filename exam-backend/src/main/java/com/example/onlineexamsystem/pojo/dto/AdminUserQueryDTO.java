package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends PageQueryDTO {
    private String account;
    private String username;
    private String phone;
    private Boolean loginStatus;
    private Integer role;
}
