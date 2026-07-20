package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷题目关联实体
 */
@Data
@TableName("exam_paper_question")
public class ExamPaperQuestion {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键ID
    private Integer paperId; // 试卷ID
    private Integer questionId; // 题目ID
    private Integer paperScore; // 题目在试卷中的分值
    private LocalDateTime createTime; // 创建时间
}
