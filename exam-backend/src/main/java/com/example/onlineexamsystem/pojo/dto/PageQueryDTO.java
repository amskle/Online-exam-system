package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

/**
 * 分页查询基础参数
 */
@Data
public class PageQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
