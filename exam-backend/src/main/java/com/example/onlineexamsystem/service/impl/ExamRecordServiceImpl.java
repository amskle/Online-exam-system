package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.mapper.ExamRecordMapper;
import com.example.onlineexamsystem.pojo.dto.ExamRecordGradeDTO;
import com.example.onlineexamsystem.pojo.dto.GradeAnswerDTO;
import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import com.example.onlineexamsystem.pojo.entity.ExamRecordAnswer;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;
import com.example.onlineexamsystem.service.ExamRecordAnswerService;
import com.example.onlineexamsystem.service.ExamRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 考试记录服务实现类
 */
@Service
@RequiredArgsConstructor
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {
    private final ExamRecordAnswerService examRecordAnswerService;

    /**
     * 查询考试记录详情（含答题明细）
     *
     * @param id 考试记录id
     * @return ExamRecordDetailVO
     */
    @Override
    public ExamRecordDetailVO detail(Integer id) {
        ExamRecord record = this.getById(id);
        if (record == null) {
            throw new BusinessException("考试记录不存在");
        }
        ExamRecordDetailVO detail = new ExamRecordDetailVO();
        BeanUtils.copyProperties(record, detail);
        List<ExamRecordAnswer> answers = examRecordAnswerService.list(
                new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, id)
        );
        detail.setAnswers(answers);
        return detail;
    }

    /**
     * 批改主观题并汇总得分
     *
     * @param dto 批改参数对象
     */
    @Override
    @Transactional
    public void grade(ExamRecordGradeDTO dto) {
        if (dto.getRecordId() == null || dto.getAnswers() == null) {
            throw new BusinessException("批改参数不能为空");
        }
        for (GradeAnswerDTO answer : dto.getAnswers()) {
            ExamRecordAnswer update = new ExamRecordAnswer();
            update.setId(answer.getAnswerId());
            update.setScore(answer.getScore());
            update.setJudgement(answer.getJudgement());
            examRecordAnswerService.updateById(update);
        }
        List<ExamRecordAnswer> answers = examRecordAnswerService.list(
                new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, dto.getRecordId())
        );
        int score = answers.stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).sum();
        ExamRecord record = new ExamRecord();
        record.setId(dto.getRecordId());
        record.setScore(score);
        this.updateById(record);
    }
}
