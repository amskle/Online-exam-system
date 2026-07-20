package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仪表盘总览数据 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVO {
    private Long userCount; // 学生+教师总数
    private Long adminCount; // 管理员总数
    private Long paperCount; // 试卷总数
    private Long recordCount; // 考试记录总数
    private Long questionCount; // 题目总数
    private Long subjectCount; // 科目总数
    private List<NameValueVO> questionTypeStats; // 各题型数量统计
    private List<NameValueVO> subjectQuestionStats; // 各科目题目数量统计
    private List<TrendStatsVO> trendStats; // 趋势数据
}
