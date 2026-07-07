package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;

import java.util.Map;

@Data
public class QuestionTypeConfigDTO {
    private Integer type;
    private Integer count;
    private Integer scorePerQuestion;
    private Map<Integer, Integer> difficultyDist;
}
