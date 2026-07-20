package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

/**
 * 学生答题参数
 */
@Data
public class StudentQuestionAnswerDTO {
    private Integer questionId;
    private String userAnswer;
}
