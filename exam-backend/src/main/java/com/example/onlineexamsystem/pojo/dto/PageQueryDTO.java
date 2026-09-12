package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

/**
 * 分页查询基础参数
 */
@Data
public class PageQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;

    public Integer getPageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
