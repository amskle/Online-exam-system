package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考试记录 Mapper 接口
 */
@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {
}
