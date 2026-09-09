package com.example.onlineexamsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.entity.WrongQuestion;
import java.util.List;

/**
 * 错题服务接口
 */
public interface WrongQuestionService extends IService<WrongQuestion> {
    /** Caller must hold this user's row lock in the surrounding exam transaction. */
    void recordWrongAnswers(List<WrongQuestion> answers);
}
