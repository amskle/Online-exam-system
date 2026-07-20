package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷 Mapper 接口
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {
}
