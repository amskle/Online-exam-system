package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试答题明细实体
 */
@Data
@TableName("exam_record_answer")
public class ExamRecordAnswer {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键ID
    private Integer recordId; // 考试记录ID
    private Integer questionId; // 题目ID
    private Integer type; // 题目类型（1:单选题, 2:多选题, 3:判断题, 4:主观题）
    private String questionContent; // 题目内容
    private String options; // 题目选项（JSON）
    private String userAnswer; // 用户答案
    private String correctAnswer; // 正确答案
    private Integer fullScore; // 题目满分
    private Integer score; // 得分
    private String judgement; // 批改评语
    private LocalDateTime createTime; // 创建时间
}
