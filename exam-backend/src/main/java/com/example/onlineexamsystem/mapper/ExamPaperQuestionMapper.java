package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.ExamPaperQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷题目关联 Mapper 接口
 */
@Mapper
public interface ExamPaperQuestionMapper extends BaseMapper<ExamPaperQuestion> {
}
