package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import lombok.Data;

import java.util.List;

@Data
public class ExamPaperDetailVO extends ExamPaper {
    private List<ExamPaperQuestionVO> questions;
}
