package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    private List<T> records; // 当前页数据
    private Long total; // 总记录数
}
