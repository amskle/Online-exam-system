package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.Question;
import lombok.Data;

@Data
public class ExamPaperQuestionVO extends Question {
    private Integer paperScore;
}
