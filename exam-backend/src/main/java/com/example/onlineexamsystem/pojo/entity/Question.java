package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目实体
 */
@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键ID
    private Integer subjectId; // 科目ID
    private String subjectName; // 科目名称
    private Integer type; // 题目类型（1:单选题, 2:多选题, 3:判断题, 4:主观题）
    private Integer difficulty; // 难度（1:简单, 2:中等, 3:困难）
    private String content; // 题目内容
    private String options; // 选项（JSON）
    private String answer; // 正确答案
    private String analysis; // 题目解析
    private Integer score; // 默认分值
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime; // 创建时间
}
