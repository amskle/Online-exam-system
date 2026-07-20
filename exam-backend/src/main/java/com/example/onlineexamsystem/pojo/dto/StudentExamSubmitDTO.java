package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生提交考试参数
 */
@Data
public class StudentExamSubmitDTO {
    private Integer recordId;
    private Integer paperId;
    private List<StudentQuestionAnswerDTO> answers;
}
