package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 考试批改参数
 */
@Data
public class ExamRecordGradeDTO {
    private Integer recordId;
    private List<GradeAnswerDTO> answers;
}
