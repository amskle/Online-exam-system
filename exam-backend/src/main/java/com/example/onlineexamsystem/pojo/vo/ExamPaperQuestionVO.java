package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.Question;
import lombok.Data;

/**
 * 试卷题目 VO（含试卷内分值）
 */
@Data
public class ExamPaperQuestionVO extends Question {
    private Integer paperScore; // 试卷内分值
}
