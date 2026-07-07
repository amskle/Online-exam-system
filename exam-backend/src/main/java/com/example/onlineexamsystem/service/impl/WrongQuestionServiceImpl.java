package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.mapper.WrongQuestionMapper;
import com.example.onlineexamsystem.pojo.entity.WrongQuestion;
import com.example.onlineexamsystem.service.WrongQuestionService;
import org.springframework.stereotype.Service;

@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion> implements WrongQuestionService {
}
