package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExamRecordQueryDTO extends PageQueryDTO {
    private String paperTitle;
    private String username;
    private Integer status;
}
