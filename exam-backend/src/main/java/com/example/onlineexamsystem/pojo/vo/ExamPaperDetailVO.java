package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 试卷详情 VO（含题目列表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamPaperDetailVO extends ExamPaper {
    private List<ExamPaperQuestionVO> questions;
}
