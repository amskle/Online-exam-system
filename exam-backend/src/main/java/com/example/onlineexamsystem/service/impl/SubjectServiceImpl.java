package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.mapper.SubjectMapper;
import com.example.onlineexamsystem.pojo.entity.Subject;
import com.example.onlineexamsystem.service.SubjectService;
import org.springframework.stereotype.Service;

/**
 * 科目服务实现类
 */
@Service
public class SubjectServiceImpl extends ServiceImpl<SubjectMapper, Subject> implements SubjectService {
}
