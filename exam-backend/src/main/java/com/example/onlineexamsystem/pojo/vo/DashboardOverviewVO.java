package com.example.onlineexamsystem.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVO {
    private Long userCount;
    private Long adminCount;
    private Long paperCount;
    private Long recordCount;
    private Long questionCount;
    private Long subjectCount;
    private List<NameValueVO> questionTypeStats;
    private List<NameValueVO> subjectQuestionStats;
    private List<TrendStatsVO> trendStats;
}
