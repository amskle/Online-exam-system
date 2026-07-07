package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionQueryDTO extends PageQueryDTO {
    private Integer subjectId;
    private Integer type;
    private Integer difficulty;
}
