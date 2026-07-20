package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.mapper.ExamPaperQuestionMapper;
import com.example.onlineexamsystem.pojo.entity.ExamPaperQuestion;
import com.example.onlineexamsystem.service.ExamPaperQuestionService;
import org.springframework.stereotype.Service;

/**
 * 试卷题目关联服务实现类
 */
@Service
public class ExamPaperQuestionServiceImpl extends ServiceImpl<ExamPaperQuestionMapper, ExamPaperQuestion> implements ExamPaperQuestionService {
}
