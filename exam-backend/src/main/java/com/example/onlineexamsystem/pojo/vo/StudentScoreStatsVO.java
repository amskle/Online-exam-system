package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentScoreStatsVO {
    private Long recordCount;
    private Double averageScoreRate;
    private Long highestScoreRate;
    private Long lowestScoreRate;
    private Long passCount;
    private Double passRate;
    private List<NameValueVO> scoreDistribution;
    private List<NameValueVO> topStudentScores;
}
