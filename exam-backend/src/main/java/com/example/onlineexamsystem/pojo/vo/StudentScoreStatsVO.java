package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 学生成绩统计 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentScoreStatsVO {
    private Long recordCount; // 考试记录总数
    private Double averageScoreRate; // 平均得分率
    private Long highestScoreRate; // 最高得分率
    private Long lowestScoreRate; // 最低得分率
    private Long passCount; // 及格人数
    private Double passRate; // 及格率
    private List<NameValueVO> scoreDistribution; // 分数区间分布
    private List<NameValueVO> topStudentScores; // 高分学生排名
}
