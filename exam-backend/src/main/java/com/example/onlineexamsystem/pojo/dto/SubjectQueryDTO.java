package com.example.onlineexamsystem.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科目查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectQueryDTO extends PageQueryDTO {
    private String name;
}
