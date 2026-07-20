package com.example.onlineexamsystem.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷保存/更新参数
 */
@Data
public class ExamPaperSaveDTO {
    private Integer id;
    private String title;
    private Integer subjectId;
    private String subjectName;
    private Integer totalScore;
    private Integer duration;
    private Integer maxAttempts;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private List<ExamPaperQuestionDTO> questions;
}
