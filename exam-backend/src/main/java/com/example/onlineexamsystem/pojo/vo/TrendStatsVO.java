package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 趋势统计 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendStatsVO {
    private String date; // 日期
    private Long users; // 新增用户数
    private Long exams; // 新增考试数
    private Long questions; // 新增题目数
}
