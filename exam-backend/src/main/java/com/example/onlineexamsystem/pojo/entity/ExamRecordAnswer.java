package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_record_answer")
public class ExamRecordAnswer {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer recordId;
    private Integer questionId;
    private Integer type;
    private String questionContent;
    private String options;
    private String userAnswer;
    private String correctAnswer;
    private Integer fullScore;
    private Integer score;
    private String judgement;
    private LocalDateTime createTime;
}
