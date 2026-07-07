package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.ExamPaperQueryDTO;
import com.example.onlineexamsystem.pojo.dto.ExamRecordQueryDTO;
import com.example.onlineexamsystem.pojo.dto.StudentExamSubmitDTO;
import com.example.onlineexamsystem.pojo.entity.*;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.*;
import com.example.onlineexamsystem.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
@Auth(1)
public class StudentExamController {
    private final ExamPaperService examPaperService;
    private final ExamRecordService examRecordService;
    private final ExamRecordAnswerService examRecordAnswerService;
    private final QuestionService questionService;
    private final BaseUserService baseUserService;
    private final WrongQuestionService wrongQuestionService;
    private final ExamPaperQuestionService examPaperQuestionService;

    @GetMapping("/examPapers/listPage")
    public Result<PageVO<ExamPaper>> listAvailablePapers(ExamPaperQueryDTO query) {
        Page<ExamPaper> page = examPaperService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getStatus, 1)
                        .like(StringUtils.hasText(query.getTitle()), ExamPaper::getTitle, query.getTitle())
                        .orderByDesc(ExamPaper::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/examPapers/{id}/detail")
    public Result<ExamPaperDetailVO> paperDetail(@PathVariable Integer id) {
        return Result.success(examPaperService.detail(id));
    }

    @PostMapping("/examRecords/start")
    public Result<ExamRecord> start(@RequestParam Integer paperId) {
        Integer userId = UserContext.getUserId();
        BaseUser user = baseUserService.getById(userId);
        ExamPaper paper = examPaperService.getById(paperId);
        if (paper == null || !Objects.equals(paper.getStatus(), 1)) {
            throw new BusinessException("试卷不可参加");
        }
        ExamRecord existing = examRecordService.getOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getUserId, userId)
                        .eq(ExamRecord::getPaperId, paperId)
                        .last("limit 1")
        );
        if (existing != null) {
            if (Objects.equals(existing.getStatus(), 0)) {
                return Result.success(existing);
            }
            int attempted = existing.getAttemptCount() == null ? 1 : existing.getAttemptCount();
            int maxAttempts = paper.getMaxAttempts() == null || paper.getMaxAttempts() < 1 ? 1 : paper.getMaxAttempts();
            if (attempted >= maxAttempts) {
                throw new BusinessException("考试次数已用完");
            }
            examRecordAnswerService.remove(new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, existing.getId()));
            existing.setUsername(user.getUsername());
            existing.setPaperTitle(paper.getTitle());
            existing.setScore(0);
            existing.setTotalScore(paper.getTotalScore());
            existing.setPassScore(60);
            existing.setAttemptCount(attempted + 1);
            existing.setStatus(0);
            existing.setStartTime(LocalDateTime.now());
            existing.setSubmitTime(null);
            existing.setCreateTime(LocalDateTime.now());
            examRecordService.updateById(existing);
            return Result.success(existing);
        }
        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setUsername(user.getUsername());
        record.setPaperId(paperId);
        record.setPaperTitle(paper.getTitle());
        record.setScore(0);
        record.setTotalScore(paper.getTotalScore());
        record.setPassScore(60);
        record.setAttemptCount(1);
        record.setStatus(0);
        record.setStartTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        examRecordService.save(record);
        return Result.success(record);
    }

    @PostMapping("/examRecords/submit")
    public Result<Void> submit(@RequestBody StudentExamSubmitDTO dto) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = examRecordService.getById(dto.getRecordId());
        if (record == null || !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException("考试记录不存在");
        }
        examRecordAnswerService.remove(new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, record.getId()));
        int totalScore = 0;
        if (dto.getAnswers() != null) {
            for (var answerDTO : dto.getAnswers()) {
                Question question = questionService.getById(answerDTO.getQuestionId());
                if (question == null) {
                    continue;
                }
                int fullScore = getPaperQuestionScore(record.getPaperId(), question);
                ExamRecordAnswer answer = new ExamRecordAnswer();
                answer.setRecordId(record.getId());
                answer.setQuestionId(question.getId());
                answer.setType(question.getType());
                answer.setQuestionContent(question.getContent());
                answer.setOptions(question.getOptions());
                answer.setUserAnswer(answerDTO.getUserAnswer());
                answer.setCorrectAnswer(question.getAnswer());
                answer.setFullScore(fullScore);
                answer.setCreateTime(LocalDateTime.now());
                boolean objective = question.getType() != null && question.getType() != 4;
                boolean correct = objective && normalizeAnswer(question.getAnswer()).equals(normalizeAnswer(answerDTO.getUserAnswer()));
                answer.setScore(correct ? fullScore : 0);
                answer.setJudgement(correct ? "正确" : "错误");
                examRecordAnswerService.save(answer);
                totalScore += answer.getScore();
                if (objective && !correct) {
                    saveWrongQuestion(userId, question, answerDTO.getUserAnswer());
                }
            }
        }
        record.setScore(totalScore);
        record.setStatus(1);
        record.setSubmitTime(LocalDateTime.now());
        examRecordService.updateById(record);
        return Result.success();
    }

    @GetMapping("/examRecords/listPage")
    public Result<PageVO<ExamRecord>> myRecords(ExamRecordQueryDTO query) {
        Integer userId = UserContext.getUserId();
        Page<ExamRecord> page = examRecordService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getUserId, userId)
                        .like(StringUtils.hasText(query.getPaperTitle()), ExamRecord::getPaperTitle, query.getPaperTitle())
                        .eq(query.getStatus() != null, ExamRecord::getStatus, query.getStatus())
                        .orderByDesc(ExamRecord::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/examRecords/{id}/detail")
    public Result<ExamRecordDetailVO> myRecordDetail(@PathVariable Integer id) {
        ExamRecord record = examRecordService.getById(id);
        if (record == null || !Objects.equals(record.getUserId(), UserContext.getUserId())) {
            throw new BusinessException("考试记录不存在");
        }
        return Result.success(examRecordService.detail(id));
    }

    @GetMapping("/wrongQuestions/listPage")
    public Result<PageVO<WrongQuestion>> wrongQuestions(ExamPaperQueryDTO query, Boolean mastered) {
        Integer userId = UserContext.getUserId();
        Page<WrongQuestion> page = wrongQuestionService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .eq(query.getSubjectId() != null, WrongQuestion::getSubjectId, query.getSubjectId())
                        .eq(mastered != null, WrongQuestion::getMastered, mastered)
                        .orderByDesc(WrongQuestion::getLastWrongTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    @PutMapping("/wrongQuestions/{id}/mastered")
    public Result<Void> updateMastered(@PathVariable Integer id, @RequestParam Boolean mastered) {
        WrongQuestion wrongQuestion = wrongQuestionService.getById(id);
        if (wrongQuestion == null || !Objects.equals(wrongQuestion.getUserId(), UserContext.getUserId())) {
            throw new BusinessException("错题不存在");
        }
        wrongQuestion.setMastered(mastered);
        wrongQuestionService.updateById(wrongQuestion);
        return Result.success();
    }

    @DeleteMapping("/wrongQuestions/{id}")
    public Result<Void> deleteWrongQuestion(@PathVariable Integer id) {
        WrongQuestion wrongQuestion = wrongQuestionService.getById(id);
        if (wrongQuestion == null || !Objects.equals(wrongQuestion.getUserId(), UserContext.getUserId())) {
            throw new BusinessException("错题不存在");
        }
        wrongQuestionService.removeById(id);
        return Result.success();
    }

    private int getPaperQuestionScore(Integer paperId, Question question) {
        ExamPaperQuestion relation = examPaperQuestionService.getOne(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, paperId)
                        .eq(ExamPaperQuestion::getQuestionId, question.getId())
                        .last("limit 1")
        );
        if (relation != null && relation.getPaperScore() != null) {
            return relation.getPaperScore();
        }
        return question.getScore() == null ? 0 : question.getScore();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return Arrays.stream(answer.replace("，", ",").replace(" ", "").trim().split(","))
                .filter(StringUtils::hasText)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private void saveWrongQuestion(Integer userId, Question question, String userAnswer) {
        WrongQuestion existed = wrongQuestionService.getOne(
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .eq(WrongQuestion::getQuestionId, question.getId())
                        .last("limit 1")
        );
        if (existed != null) {
            existed.setWrongCount((existed.getWrongCount() == null ? 0 : existed.getWrongCount()) + 1);
            existed.setUserAnswer(userAnswer);
            existed.setCorrectAnswer(question.getAnswer());
            existed.setMastered(false);
            existed.setLastWrongTime(LocalDateTime.now());
            wrongQuestionService.updateById(existed);
            return;
        }
        WrongQuestion wrongQuestion = new WrongQuestion();
        BeanUtils.copyProperties(question, wrongQuestion);
        wrongQuestion.setId(null);
        wrongQuestion.setUserId(userId);
        wrongQuestion.setQuestionId(question.getId());
        wrongQuestion.setUserAnswer(userAnswer);
        wrongQuestion.setCorrectAnswer(question.getAnswer());
        wrongQuestion.setWrongCount(1);
        wrongQuestion.setMastered(false);
        wrongQuestion.setLastWrongTime(LocalDateTime.now());
        wrongQuestion.setCreateTime(LocalDateTime.now());
        wrongQuestionService.save(wrongQuestion);
    }
}
