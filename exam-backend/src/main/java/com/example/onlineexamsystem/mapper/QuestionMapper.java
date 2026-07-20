package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目 Mapper 接口
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
