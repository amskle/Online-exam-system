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
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        ExamRecord existing = this.getById(dto.getRecordId());
        if (existing == null) {
            throw new BusinessException("考试记录不存在");
        }
        List<ExamRecordAnswer> answers = examRecordAnswerService.list(
                new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, dto.getRecordId())
        );
        Map<Integer, ExamRecordAnswer> storedById = answers.stream()
                .collect(Collectors.toMap(ExamRecordAnswer::getId, Function.identity()));
        Set<Integer> answerIds = new HashSet<>();
        for (GradeAnswerDTO answer : dto.getAnswers()) {
            if (answer.getAnswerId() == null || answer.getScore() == null) {
                throw new BusinessException("批改题目和分数不能为空");
            }
            if (!answerIds.add(answer.getAnswerId())) {
                throw new BusinessException("同一道题不能重复批改");
            }
            ExamRecordAnswer stored = storedById.get(answer.getAnswerId());
            if (stored == null) {
                throw new BusinessException("批改内容不属于该考试记录");
            }
            if (!Integer.valueOf(4).equals(stored.getType())) {
                throw new BusinessException("只能人工批改主观题");
            }
            int fullScore = stored.getFullScore() == null ? 0 : stored.getFullScore();
            if (answer.getScore() < 0 || answer.getScore() > fullScore) {
                throw new BusinessException("批改分数必须在0到题目满分之间");
            }
            ExamRecordAnswer update = new ExamRecordAnswer();
            update.setId(answer.getAnswerId());
            update.setScore(answer.getScore());
            update.setJudgement(answer.getJudgement());
            examRecordAnswerService.updateById(update);
            stored.setScore(answer.getScore());
            stored.setJudgement(answer.getJudgement());
        }
        int score = answers.stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).sum();
        ExamRecord record = new ExamRecord();
        record.setId(dto.getRecordId());
        record.setScore(score);
        // 更新历史最高成绩
        int currentHighest = existing != null && existing.getHighestScore() != null ? existing.getHighestScore() : 0;
        if (score > currentHighest) {
            record.setHighestScore(score);
        }
        this.updateById(record);
    }
}
