package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.Question;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷题目 VO（含试卷内分值）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamPaperQuestionVO extends Question {
    private Integer paperScore; // 试卷内分值
}
