package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
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

/**
 * 学生考试控制器
 */
/**
 * 学生考试控制器
 */
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

    /**
     * 分页查询可参加的试卷列表
     *
     * @return Result<PageVO<ExamPaper>>
     */
    /**
     * 查询可用试卷列表
     *
     * @return Result<PageVO<ExamPaper>>
     */
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

    /**
     * 查询试卷详情（含题目列表）
     *
     * @return Result<ExamPaperDetailVO>
     */
    /**
     * 获取试卷详情
     *
     * @return Result<ExamPaperDetailVO>
     */
    @GetMapping("/examPapers/{id}/detail")
    public Result<ExamPaperDetailVO> paperDetail(@PathVariable Integer id) {
        return Result.success(examPaperService.detail(id));
    }

    /**
     * 开始考试（创建或续考考试记录）
     *
     * @return Result<ExamRecord>
     */
    /**
     * 开始考试（创建考试记录）
     *
     * @return Result<ExamRecord>
     */
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

    /**
     * 提交考试（自动判分客观题并记录错题）
     *
     * @return Result<Void>
     */
    /**
     * 提交考试答案
     *
     * @return Result<Void>
     */
    @PostMapping("/examRecords/submit")
    public Result<Void> submit(@RequestBody StudentExamSubmitDTO dto) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = examRecordService.getById(dto.getRecordId());
        if (record == null || !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException("考试记录不存在");
        }
        // 后端时间校验：防止前端绕过倒计时
        ExamPaper paper = examPaperService.getById(record.getPaperId());
        if (paper != null && record.getStartTime() != null && paper.getDuration() != null) {
            LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new BusinessException("考试时间已结束，无法提交");
            }
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

    /**
     * 上报切屏/离开考试页面行为
     *
     * @return Result<Void>
     */
    /**
     * 上报切屏警告
     *
     * @return Result<Void>
     */
    @PostMapping("/examRecords/warn")
    public Result<Void> warn(@RequestParam Integer recordId) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = examRecordService.getById(recordId);
        if (record == null || !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException("考试记录不存在");
        }
        if (!Objects.equals(record.getStatus(), 0)) {
            throw new BusinessException("考试已结束");
        }
        int count = record.getWarningCount() == null ? 0 : record.getWarningCount();
        record.setWarningCount(count + 1);
        examRecordService.updateById(record);
        return Result.success();
    }

    /**
     * 分页查询我的考试记录
     *
     * @return Result<PageVO<ExamRecord>>
     */
    /**
     * 查询我的考试记录
     *
     * @return Result<PageVO<ExamRecord>>
     */
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

    /**
     * 查询我的考试记录详情（含答题明细）
     *
     * @return Result<ExamRecordDetailVO>
     */
    /**
     * 获取考试记录详情
     *
     * @return Result<ExamRecordDetailVO>
     */
    @GetMapping("/examRecords/{id}/detail")
    public Result<ExamRecordDetailVO> myRecordDetail(@PathVariable Integer id) {
        ExamRecord record = examRecordService.getById(id);
        if (record == null || !Objects.equals(record.getUserId(), UserContext.getUserId())) {
            throw new BusinessException("考试记录不存在");
        }
        return Result.success(examRecordService.detail(id));
    }

    /**
     * 分页查询错题本
     *
     * @return Result<PageVO<WrongQuestion>>
     */
    /**
     * 分页查询我的错题
     *
     * @return Result<PageVO<WrongQuestion>>
     */
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

    /**
     * 修改错题掌握状态
     *
     * @return Result<Void>
     */
    /**
     * 更新错题掌握状态
     *
     * @return Result<Void>
     */
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

    /**
     * 删除错题
     *
     * @return Result<Void>
     */
    /**
     * 删除错题记录
     *
     * @return Result<Void>
     */
    @DeleteMapping("/wrongQuestions/{id}")
    public Result<Void> deleteWrongQuestion(@PathVariable Integer id) {
        WrongQuestion wrongQuestion = wrongQuestionService.getById(id);
        if (wrongQuestion == null || !Objects.equals(wrongQuestion.getUserId(), UserContext.getUserId())) {
            throw new BusinessException("错题不存在");
        }
        wrongQuestionService.removeById(id);
        return Result.success();
    }

    /**
     * 获取试卷中某题目的分值
     *
     * @param paperId  试卷id
     * @param question 题目对象
     * @return 该题在试卷中的分值，取不到则回退题目本身分值
     */
    /**
     * 获取题目在试卷中的分值
     *
     * @param paperId 试卷ID
     * @param question 题目对象
     * @return int 题目分值
     */
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

    /**
     * 归一化答案：去空格、统一分隔符并排序，用于客观题判分
     *
     * @param answer 原始答案
     * @return 归一化后的答案字符串
     */
    /**
     * 标准化答案字符串（去空格、排序、统一分隔符）
     *
     * @param answer 原始答案
     * @return 标准化后的答案
     */
    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return Arrays.stream(answer.replace("，", ",").replace(" ", "").trim().split(","))
                .filter(StringUtils::hasText)
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * 保存错题，已存在则累加错误次数并更新，否则新增
     *
     * @param userId     用户id
     * @param question   题目对象
     * @param userAnswer 学生作答
     */
    /**
     * 保存错题记录
     *
     * @param userId 用户ID
     * @param question 题目对象
     * @param userAnswer 用户答案
     */
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
