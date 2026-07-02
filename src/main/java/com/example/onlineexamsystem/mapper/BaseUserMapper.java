package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基础用户信息接口
 */
@Mapper
public interface BaseUserMapper extends BaseMapper<BaseUser> {

}
