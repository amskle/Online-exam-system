package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学生答题参数
 */
@Data
public class StudentQuestionAnswerDTO {
    @NotNull(message = "题目不能为空")
    private Integer questionId;
    private String userAnswer;
}
