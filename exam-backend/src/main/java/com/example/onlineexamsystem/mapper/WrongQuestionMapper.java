package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错题 Mapper 接口
 */
@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {
}
