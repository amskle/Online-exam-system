package com.example.onlineexamsystem.pojo.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AccountStatusEnum {

    NORMAL(false, "正常"),
    FORBIDDEN(true, "封号");
    private final Boolean StatusCode;
    private final String StatusText;
}
