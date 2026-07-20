package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.mapper.QuestionMapper;
import com.example.onlineexamsystem.pojo.entity.Question;
import com.example.onlineexamsystem.service.QuestionService;
import org.springframework.stereotype.Service;

/**
 * 题目服务实现类
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
}
