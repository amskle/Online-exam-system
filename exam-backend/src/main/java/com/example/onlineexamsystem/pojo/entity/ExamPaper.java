package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷实体
 */
@Data
@TableName("exam_paper")
public class ExamPaper {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键ID
    private String title; // 试卷标题
    private Integer subjectId; // 科目ID
    private String subjectName; // 科目名称
    private Integer totalScore; // 总分
    private Integer duration; // 考试时长（分钟）
    private Integer maxAttempts; // 最大考试次数
    private Integer status; // 状态（0:未发布, 1:已发布）
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime; // 开始时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime; // 结束时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime; // 创建时间
}
