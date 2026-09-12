package com.example.onlineexamsystem.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 学生提交考试参数
 */
@Data
public class StudentExamSubmitDTO {
    @NotNull(message = "考试记录不能为空")
    private Integer recordId;
    private Integer paperId;
    @Valid
    private List<StudentQuestionAnswerDTO> answers;
}
