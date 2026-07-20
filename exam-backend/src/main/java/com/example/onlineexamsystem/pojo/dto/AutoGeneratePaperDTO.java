package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 自动组卷参数
 */
@Data
public class AutoGeneratePaperDTO {
    private String title;
    private Integer subjectId;
    private String subjectName;
    private Integer totalScore;
    private Integer duration;
    private Integer maxAttempts;
    private List<QuestionTypeConfigDTO> typeConfigs;
}
