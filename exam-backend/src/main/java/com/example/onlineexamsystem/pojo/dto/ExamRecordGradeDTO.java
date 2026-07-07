package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExamRecordGradeDTO {
    private Integer recordId;
    private List<GradeAnswerDTO> answers;
}
