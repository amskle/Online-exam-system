package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考试记录查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamRecordQueryDTO extends PageQueryDTO {
    private String paperTitle;
    private String username;
    private Integer status;
}
