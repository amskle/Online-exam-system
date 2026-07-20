package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

/**
 * 试卷题目关联参数
 */
@Data
public class ExamPaperQuestionDTO {
    private Integer questionId;
    private Integer paperScore;
}
