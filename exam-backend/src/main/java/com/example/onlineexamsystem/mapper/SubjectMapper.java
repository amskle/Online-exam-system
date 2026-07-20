package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.Subject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 科目 Mapper 接口
 */
@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {
}
