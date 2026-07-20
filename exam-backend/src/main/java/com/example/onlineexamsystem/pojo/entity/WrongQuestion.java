package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题实体
 */
@Data
@TableName("wrong_question")
public class WrongQuestion {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键ID
    private Integer userId; // 用户ID
    private Integer questionId; // 题目ID
    private Integer subjectId; // 科目ID
    private String subjectName; // 科目名称
    private Integer type; // 题目类型（1:单选题, 2:多选题, 3:判断题, 4:主观题）
    private String content; // 题目内容
    private String options; // 选项（JSON）
    private String userAnswer; // 用户错误答案
    private String correctAnswer; // 正确答案
    private String analysis; // 题目解析
    private Integer wrongCount; // 错误次数
    private Boolean mastered; // 是否已掌握
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastWrongTime; // 最近一次错误时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime; // 创建时间
}
