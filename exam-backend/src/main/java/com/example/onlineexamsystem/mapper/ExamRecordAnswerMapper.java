package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.ExamRecordAnswer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考试答题明细 Mapper 接口
 */
@Mapper
public interface ExamRecordAnswerMapper extends BaseMapper<ExamRecordAnswer> {
}
