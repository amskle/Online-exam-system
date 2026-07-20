package com.example.onlineexamsystem.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试记录实体
 */
@Data
@TableName("exam_record")
public class ExamRecord {
    @TableId(type = IdType.AUTO)
    private Integer id; // 主键ID
    private Integer userId; // 用户ID
    private String username; // 用户名
    private Integer paperId; // 试卷ID
    private String paperTitle; // 试卷标题
    private Integer score; // 得分
    private Integer totalScore; // 总分
    private Integer passScore; // 及格分
    private Integer attemptCount; // 当前考试次数
    private Integer warningCount; // 切屏/离开页面次数
    private Integer status; // 状态（0:考试中, 1:已交卷）
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime; // 开始时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime submitTime; // 交卷时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime; // 创建时间
}
