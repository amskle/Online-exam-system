package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.mapper.ExamRecordAnswerMapper;
import com.example.onlineexamsystem.pojo.entity.ExamRecordAnswer;
import com.example.onlineexamsystem.service.ExamRecordAnswerService;
import org.springframework.stereotype.Service;

/**
 * 考试答题明细服务实现类
 */
@Service
public class ExamRecordAnswerServiceImpl extends ServiceImpl<ExamRecordAnswerMapper, ExamRecordAnswer> implements ExamRecordAnswerService {
}
