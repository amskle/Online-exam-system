package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExamPaperQueryDTO extends PageQueryDTO {
    private String title;
    private Integer subjectId;
    private Integer status;
}
