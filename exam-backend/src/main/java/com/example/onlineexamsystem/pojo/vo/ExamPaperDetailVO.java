package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import lombok.Data;

import java.util.List;

/**
 * 试卷详情 VO（含题目列表）
 */
@Data
public class ExamPaperDetailVO extends ExamPaper {
    private List<ExamPaperQuestionVO> questions;
}
