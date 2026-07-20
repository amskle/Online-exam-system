package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 名值对 VO（通用图表数据）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NameValueVO {
    private String name;
    private Long value;
}
