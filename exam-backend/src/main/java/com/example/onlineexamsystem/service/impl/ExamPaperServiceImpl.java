package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.mapper.ExamPaperMapper;
import com.example.onlineexamsystem.pojo.dto.AutoGeneratePaperDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperQuestionDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperSaveDTO;
import com.example.onlineexamsystem.pojo.dto.QuestionTypeConfigDTO;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import com.example.onlineexamsystem.pojo.entity.ExamPaperQuestion;
import com.example.onlineexamsystem.pojo.entity.Question;
import com.example.onlineexamsystem.pojo.entity.Subject;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;
import com.example.onlineexamsystem.pojo.vo.ExamPaperQuestionVO;
import com.example.onlineexamsystem.service.ExamPaperQuestionService;
import com.example.onlineexamsystem.service.ExamPaperService;
import com.example.onlineexamsystem.service.QuestionService;
import com.example.onlineexamsystem.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 试卷服务实现类
 */
@Service
@RequiredArgsConstructor
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamPaperService {
    private final ExamPaperQuestionService examPaperQuestionService;
    private final QuestionService questionService;
    private final SubjectService subjectService;

    /**
     * 保存试卷（含题目关联）
     *
     * @param dto 试卷保存参数对象
     */
    @Override
    @Transactional
    public void savePaper(ExamPaperSaveDTO dto) {
        ExamPaper paper = new ExamPaper();
        BeanUtils.copyProperties(dto, paper);
        paper.setId(null);
        paper.setMaxAttempts(normalizeMaxAttempts(dto.getMaxAttempts()));
        paper.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        paper.setCreateTime(LocalDateTime.now());
        paper.setAutoGradeEnabled(true);
        validatePaper(dto);
        paper.setSubjectName(resolveSubjectName(dto.getSubjectId()));
        this.save(paper);
        savePaperQuestions(paper.getId(), dto.getQuestions());
    }

    /**
     * 修改试卷（先删除旧题目关联，再重建）
     *
     * @param dto 试卷保存参数对象
     */
    @Override
    @Transactional
    public void updatePaper(ExamPaperSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("试卷id不能为空");
        }
        ExamPaper paper = new ExamPaper();
        BeanUtils.copyProperties(dto, paper);
        paper.setMaxAttempts(normalizeMaxAttempts(dto.getMaxAttempts()));
        validatePaper(dto);
        paper.setSubjectName(resolveSubjectName(dto.getSubjectId()));
        if (this.getById(dto.getId()) == null) {
            throw new BusinessException("试卷不存在");
        }
        this.updateById(paper);
        examPaperQuestionService.remove(new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, dto.getId()));
        savePaperQuestions(dto.getId(), dto.getQuestions());
    }

    /**
     * 查询试卷详情（含题目列表）
     *
     * @param id 试卷id
     * @return ExamPaperDetailVO
     */
    @Override
    public ExamPaperDetailVO detail(Integer id) {
        ExamPaper paper = this.getById(id);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        ExamPaperDetailVO detail = new ExamPaperDetailVO();
        BeanUtils.copyProperties(paper, detail);
        List<ExamPaperQuestion> relations = examPaperQuestionService.list(
                new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, id)
        );
        Set<Integer> questionIds = relations.stream()
                .map(ExamPaperQuestion::getQuestionId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, Question> questionById = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionService.listByIds(questionIds)
                    .forEach(question -> questionById.put(question.getId(), question));
        }
        List<ExamPaperQuestionVO> questions = relations.stream().map(relation -> {
            Question question = questionById.get(relation.getQuestionId());
            ExamPaperQuestionVO vo = new ExamPaperQuestionVO();
            if (question != null) {
                BeanUtils.copyProperties(question, vo);
            }
            vo.setPaperScore(relation.getPaperScore());
            return vo;
        }).toList();
        detail.setQuestions(questions);
        return detail;
    }

    /**
     * 按题型难度配置自动组卷
     *
     * @param dto 自动组卷参数对象
     */
    @Override
    @Transactional
    public void autoGenerate(AutoGeneratePaperDTO dto) {
        validateAutoGenerate(dto);
        ExamPaper paper = new ExamPaper();
        paper.setTitle(dto.getTitle());
        paper.setSubjectId(dto.getSubjectId());
        paper.setSubjectName(resolveSubjectName(dto.getSubjectId()));
        paper.setTotalScore(dto.getTotalScore());
        paper.setDuration(dto.getDuration());
        paper.setMaxAttempts(normalizeMaxAttempts(dto.getMaxAttempts()));
        paper.setStatus(0);
        paper.setAutoGradeEnabled(true);
        paper.setCreateTime(LocalDateTime.now());
        this.save(paper);

        List<ExamPaperQuestionDTO> selected = new ArrayList<>();
        for (QuestionTypeConfigDTO config : dto.getTypeConfigs()) {
            for (Map.Entry<Integer, Integer> entry : config.getDifficultyDist().entrySet()) {
                Integer difficulty = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count <= 0) {
                    continue;
                }
                List<Question> pool = questionService.list(
                        new LambdaQueryWrapper<Question>()
                                .eq(Question::getSubjectId, dto.getSubjectId())
                                .eq(Question::getType, config.getType())
                                .eq(Question::getDifficulty, difficulty)
                );
                if (pool.size() < count) {
                    throw new BusinessException("题库数量不足，无法完成自动组卷");
                }
                Collections.shuffle(pool);
                pool.stream().limit(count).forEach(question -> {
                    ExamPaperQuestionDTO relation = new ExamPaperQuestionDTO();
                    relation.setQuestionId(question.getId());
                    relation.setPaperScore(config.getScorePerQuestion());
                    selected.add(relation);
                });
            }
        }
        savePaperQuestions(paper.getId(), selected);
    }

    /**
     * 批量保存试卷题目关联
     *
     * @param paperId   试卷id
     * @param questions 题目关联参数列表
     */
    private void savePaperQuestions(Integer paperId, List<ExamPaperQuestionDTO> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("试卷至少需要一道题目");
        }
        if (questions.size() > 1000) {
            throw new BusinessException("单份试卷题目数量不能超过1000");
        }
        Set<Integer> uniqueQuestionIds = new HashSet<>();
        for (ExamPaperQuestionDTO item : questions) {
            if (item == null || item.getQuestionId() == null || item.getPaperScore() == null
                    || item.getPaperScore() <= 0 || item.getPaperScore() > 1000) {
                throw new BusinessException("试卷题目和分值配置不正确");
            }
            if (!uniqueQuestionIds.add(item.getQuestionId())) {
                throw new BusinessException("试卷中不能包含重复题目");
            }
        }
        if (questionService.listByIds(uniqueQuestionIds).size() != uniqueQuestionIds.size()) {
            throw new BusinessException("试卷包含不存在的题目");
        }
        List<ExamPaperQuestion> relations = questions.stream().map(item -> {
            ExamPaperQuestion relation = new ExamPaperQuestion();
            relation.setPaperId(paperId);
            relation.setQuestionId(item.getQuestionId());
            relation.setPaperScore(item.getPaperScore());
            relation.setCreateTime(LocalDateTime.now());
            return relation;
        }).toList();
        examPaperQuestionService.saveBatch(relations);
    }

    /**
     * 规范化最大考试次数，空或小于 1 时默认为 1
     *
     * @param maxAttempts 原始考试次数
     * @return 规范化后的考试次数
     */
    private Integer normalizeMaxAttempts(Integer maxAttempts) {
        return maxAttempts == null || maxAttempts < 1 ? 1 : maxAttempts;
    }

    private void validatePaper(ExamPaperSaveDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()
                || dto.getSubjectId() == null
                || dto.getTotalScore() == null || dto.getTotalScore() <= 0
                || dto.getDuration() == null || dto.getDuration() <= 0) {
            throw new BusinessException("试卷标题、科目、总分和考试时长配置不正确");
        }
        if (dto.getTitle().length() > 100 || dto.getTotalScore() > 10000
                || dto.getDuration() > 1440 || normalizeMaxAttempts(dto.getMaxAttempts()) > 100) {
            throw new BusinessException("试卷标题、总分、时长或考试次数超过允许范围");
        }
        if (dto.getStatus() != null && (dto.getStatus() < 0 || dto.getStatus() > 2)) {
            throw new BusinessException("试卷状态不正确");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("考试结束时间必须晚于开始时间");
        }
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            long configuredTotal = dto.getQuestions().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(ExamPaperQuestionDTO::getPaperScore)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            if (configuredTotal != dto.getTotalScore()) {
                throw new BusinessException("试卷题目分值合计必须等于试卷总分");
            }
        }
    }

    private void validateAutoGenerate(AutoGeneratePaperDTO dto) {
        if (dto == null || dto.getTitle() == null || dto.getTitle().isBlank()
                || dto.getTitle().length() > 100 || dto.getSubjectId() == null
                || dto.getTotalScore() == null || dto.getTotalScore() <= 0 || dto.getTotalScore() > 10000
                || dto.getDuration() == null || dto.getDuration() <= 0 || dto.getDuration() > 1440
                || normalizeMaxAttempts(dto.getMaxAttempts()) > 100) {
            throw new BusinessException("自动组卷的标题、科目、总分、时长或考试次数配置不正确");
        }
        if (dto.getTypeConfigs() == null || dto.getTypeConfigs().isEmpty()) {
            throw new BusinessException("请配置题型");
        }
        long configuredTotal = 0;
        Set<Integer> configuredTypes = new HashSet<>();
        for (QuestionTypeConfigDTO config : dto.getTypeConfigs()) {
            if (config == null || config.getType() == null || config.getType() < 1 || config.getType() > 4
                    || !configuredTypes.add(config.getType())
                    || config.getCount() == null || config.getCount() <= 0 || config.getCount() > 1000
                    || config.getScorePerQuestion() == null || config.getScorePerQuestion() <= 0
                    || config.getScorePerQuestion() > 1000
                    || config.getDifficultyDist() == null || config.getDifficultyDist().isEmpty()) {
                throw new BusinessException("自动组卷题型配置不正确或存在重复题型");
            }
            int difficultyCount = 0;
            for (Map.Entry<Integer, Integer> entry : config.getDifficultyDist().entrySet()) {
                if (entry.getKey() == null || entry.getKey() < 1 || entry.getKey() > 3
                        || entry.getValue() == null || entry.getValue() < 0) {
                    throw new BusinessException("自动组卷难度分布配置不正确");
                }
                difficultyCount += entry.getValue();
            }
            if (difficultyCount != config.getCount()) {
                throw new BusinessException(config.getType() + "题型难度分布合计必须等于题目数量");
            }
            configuredTotal += (long) config.getCount() * config.getScorePerQuestion();
        }
        if (configuredTotal != dto.getTotalScore()) {
            throw new BusinessException("自动组卷题目分值合计必须等于试卷总分");
        }
    }

    private String resolveSubjectName(Integer subjectId) {
        Subject subject = subjectService.getById(subjectId);
        if (subject == null) {
            throw new BusinessException("科目不存在");
        }
        return subject.getName();
    }
}
