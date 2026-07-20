package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionQueryDTO extends PageQueryDTO {
    private Integer subjectId;
    private Integer type;
    private Integer difficulty;
}
