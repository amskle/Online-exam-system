package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamPaperQueryDTO extends PageQueryDTO {
    private String title;
    private Integer subjectId;
    private Integer status;
}
