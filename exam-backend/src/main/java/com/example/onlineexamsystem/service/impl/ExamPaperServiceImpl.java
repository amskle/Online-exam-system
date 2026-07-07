package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.mapper.ExamPaperMapper;
import com.example.onlineexamsystem.pojo.dto.AutoGeneratePaperDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperQuestionDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperSaveDTO;
import com.example.onlineexamsystem.pojo.dto.QuestionTypeConfigDTO;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import com.example.onlineexamsystem.pojo.entity.ExamPaperQuestion;
import com.example.onlineexamsystem.pojo.entity.Question;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;
import com.example.onlineexamsystem.pojo.vo.ExamPaperQuestionVO;
import com.example.onlineexamsystem.service.ExamPaperQuestionService;
import com.example.onlineexamsystem.service.ExamPaperService;
import com.example.onlineexamsystem.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamPaperService {
    private final ExamPaperQuestionService examPaperQuestionService;
    private final QuestionService questionService;

    @Override
    @Transactional
    public void savePaper(ExamPaperSaveDTO dto) {
        ExamPaper paper = new ExamPaper();
        BeanUtils.copyProperties(dto, paper);
        paper.setId(null);
        paper.setMaxAttempts(normalizeMaxAttempts(dto.getMaxAttempts()));
        paper.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        paper.setCreateTime(LocalDateTime.now());
        this.save(paper);
        savePaperQuestions(paper.getId(), dto.getQuestions());
    }

    @Override
    @Transactional
    public void updatePaper(ExamPaperSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("试卷id不能为空");
        }
        ExamPaper paper = new ExamPaper();
        BeanUtils.copyProperties(dto, paper);
        paper.setMaxAttempts(normalizeMaxAttempts(dto.getMaxAttempts()));
        this.updateById(paper);
        examPaperQuestionService.remove(new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, dto.getId()));
        savePaperQuestions(dto.getId(), dto.getQuestions());
    }

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
        List<ExamPaperQuestionVO> questions = relations.stream().map(relation -> {
            Question question = questionService.getById(relation.getQuestionId());
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

    @Override
    @Transactional
    public void autoGenerate(AutoGeneratePaperDTO dto) {
        if (dto.getTypeConfigs() == null || dto.getTypeConfigs().isEmpty()) {
            throw new BusinessException("请配置题型");
        }
        ExamPaper paper = new ExamPaper();
        paper.setTitle(dto.getTitle());
        paper.setSubjectId(dto.getSubjectId());
        paper.setSubjectName(dto.getSubjectName());
        paper.setTotalScore(dto.getTotalScore());
        paper.setDuration(dto.getDuration());
        paper.setMaxAttempts(normalizeMaxAttempts(dto.getMaxAttempts()));
        paper.setStatus(0);
        paper.setCreateTime(LocalDateTime.now());
        this.save(paper);

        List<ExamPaperQuestionDTO> selected = new ArrayList<>();
        for (QuestionTypeConfigDTO config : dto.getTypeConfigs()) {
            if (config.getCount() == null || config.getCount() <= 0) {
                continue;
            }
            int difficultyCount = config.getDifficultyDist() == null ? 0 : config.getDifficultyDist().values().stream().mapToInt(Integer::intValue).sum();
            if (difficultyCount != config.getCount()) {
                throw new BusinessException(config.getType() + "题型难度分布合计必须等于题目数量");
            }
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

    private void savePaperQuestions(Integer paperId, List<ExamPaperQuestionDTO> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("试卷至少需要一道题目");
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

    private Integer normalizeMaxAttempts(Integer maxAttempts) {
        return maxAttempts == null || maxAttempts < 1 ? 1 : maxAttempts;
    }
}
