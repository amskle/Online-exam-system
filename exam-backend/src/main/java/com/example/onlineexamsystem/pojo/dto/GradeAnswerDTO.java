package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

/**
 * 批改答题参数
 */
@Data
public class GradeAnswerDTO {
    private Integer answerId;
    private Integer score;
    private String judgement;
}
