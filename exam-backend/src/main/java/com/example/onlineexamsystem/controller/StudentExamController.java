package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.ExamPaperQueryDTO;
import com.example.onlineexamsystem.pojo.dto.ExamRecordQueryDTO;
import com.example.onlineexamsystem.pojo.dto.StudentExamSubmitDTO;
import com.example.onlineexamsystem.pojo.dto.StudentQuestionAnswerDTO;
import com.example.onlineexamsystem.pojo.entity.*;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.*;
import com.example.onlineexamsystem.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        LocalDateTime now = LocalDateTime.now();
        Page<ExamPaper> page = examPaperService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getStatus, 1)
                        .and(wrapper -> wrapper.isNull(ExamPaper::getStartTime).or().le(ExamPaper::getStartTime, now))
                        .and(wrapper -> wrapper.isNull(ExamPaper::getEndTime).or().gt(ExamPaper::getEndTime, now))
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
        ExamPaperDetailVO detail = examPaperService.detail(id);
        validatePaperAvailability(detail);
        detail.getQuestions().forEach(question -> {
            question.setAnswer(null);
            question.setAnalysis(null);
            question.setCreateTime(null);
        });
        return Result.success(detail);
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
    @Transactional
    public Result<ExamRecord> start(@RequestParam Integer paperId) {
        Integer userId = UserContext.getUserId();
        BaseUser user = baseUserService.getOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getId, userId)
                .last("FOR UPDATE"));
        ExamPaper paper = examPaperService.getById(paperId);
        if (paper == null || !Objects.equals(paper.getStatus(), 1)) {
            throw new BusinessException("试卷不可参加");
        }
        validatePaperAvailability(paper);
        if (user == null) {
            throw new BusinessException("用户不存在");
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
            removeRecordAnswersIfPresent(existing.getId());
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
        record.setHighestScore(0);
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
    @Transactional
    public Result<Void> submit(@Valid @RequestBody StudentExamSubmitDTO dto) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = getOwnedRecordForUpdate(dto.getRecordId(), userId);
        if (!Objects.equals(record.getStatus(), 0)) {
            throw new BusinessException("考试已提交，请勿重复交卷");
        }
        // 后端时间校验：防止前端绕过倒计时
        ExamPaper paper = examPaperService.getById(record.getPaperId());
        validateSubmissionTime(record, paper);
        Map<Integer, Integer> scoreByQuestionId = getPaperQuestionScores(record.getPaperId());
        Map<Integer, Question> questionById = getSubmittedQuestions(dto, scoreByQuestionId);
        removeRecordAnswersIfPresent(record.getId());
        int totalScore = 0;
        List<ExamRecordAnswer> answersToSave = new ArrayList<>();
        if (dto.getAnswers() != null) {
            for (var answerDTO : dto.getAnswers()) {
                Question question = questionById.get(answerDTO.getQuestionId());
                int fullScore = scoreByQuestionId.get(answerDTO.getQuestionId());
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
                answer.setJudgement(objective ? (correct ? "正确" : "错误") : "待批改");
                answersToSave.add(answer);
                totalScore += answer.getScore();
                if (objective && !correct) {
                    saveWrongQuestion(userId, question, answerDTO.getUserAnswer());
                }
            }
        }
        if (!answersToSave.isEmpty()) {
            examRecordAnswerService.saveBatch(answersToSave);
        }
        record.setScore(totalScore);
        // 更新历史最高成绩
        int currentHighest = record.getHighestScore() == null ? 0 : record.getHighestScore();
        if (totalScore > currentHighest) {
            record.setHighestScore(totalScore);
        }
        record.setStatus(1);
        record.setSubmitTime(LocalDateTime.now());
        examRecordService.updateById(record);
        return Result.success();
    }

    /**
     * 自动保存答题进度（不交卷）
     *
     * @return Result<Void>
     */
    @PostMapping("/examRecords/save-progress")
    @Transactional
    public Result<Void> saveProgress(@Valid @RequestBody StudentExamSubmitDTO dto) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = getOwnedRecordForUpdate(dto.getRecordId(), userId);
        if (!Objects.equals(record.getStatus(), 0)) {
            throw new BusinessException("考试已结束，无法保存");
        }
        ExamPaper paper = examPaperService.getById(record.getPaperId());
        validateSubmissionTime(record, paper);
        Map<Integer, Integer> scoreByQuestionId = getPaperQuestionScores(record.getPaperId());
        Map<Integer, Question> questionById = getSubmittedQuestions(dto, scoreByQuestionId);
        if (dto.getAnswers() != null) {
            Map<Integer, ExamRecordAnswer> existingByQuestionId = new HashMap<>();
            examRecordAnswerService.list(new LambdaQueryWrapper<ExamRecordAnswer>()
                            .eq(ExamRecordAnswer::getRecordId, record.getId()))
                    .forEach(answer -> existingByQuestionId.put(answer.getQuestionId(), answer));
            List<ExamRecordAnswer> answersToSave = new ArrayList<>();
            for (var answerDTO : dto.getAnswers()) {
                Question question = questionById.get(answerDTO.getQuestionId());
                ExamRecordAnswer existing = existingByQuestionId.get(answerDTO.getQuestionId());
                ExamRecordAnswer answer;
                if (existing != null) {
                    answer = existing;
                } else {
                    answer = new ExamRecordAnswer();
                    answer.setRecordId(record.getId());
                    answer.setQuestionId(question.getId());
                    answer.setType(question.getType());
                    answer.setQuestionContent(question.getContent());
                    answer.setOptions(question.getOptions());
                    answer.setFullScore(scoreByQuestionId.get(question.getId()));
                    answer.setCreateTime(LocalDateTime.now());
                }
                answer.setUserAnswer(answerDTO.getUserAnswer());
                answersToSave.add(answer);
            }
            if (!answersToSave.isEmpty()) {
                examRecordAnswerService.saveOrUpdateBatch(answersToSave);
            }
        }
        return Result.success();
    }

    /**
     * 获取考试中的草稿答案（用于页面刷新后恢复）
     *
     * @return Result<List<StudentQuestionAnswerDTO>>
     */
    @GetMapping("/examRecords/{recordId}/draft")
    public Result<List<StudentQuestionAnswerDTO>> getDraft(@PathVariable Integer recordId) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = examRecordService.getById(recordId);
        if (record == null || !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException("考试记录不存在");
        }
        List<ExamRecordAnswer> answers = examRecordAnswerService.list(
                new LambdaQueryWrapper<ExamRecordAnswer>()
                        .eq(ExamRecordAnswer::getRecordId, recordId)
        );
        List<StudentQuestionAnswerDTO> result = answers.stream().map(a -> {
            StudentQuestionAnswerDTO dto = new StudentQuestionAnswerDTO();
            dto.setQuestionId(a.getQuestionId());
            dto.setUserAnswer(a.getUserAnswer());
            return dto;
        }).toList();
        return Result.success(result);
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
    @Transactional
    public Result<Void> warn(@RequestParam Integer recordId) {
        Integer userId = UserContext.getUserId();
        ExamRecord record = getOwnedRecordForUpdate(recordId, userId);
        if (!Objects.equals(record.getStatus(), 0)) {
            throw new BusinessException("考试已结束");
        }
        int count = record.getWarningCount() == null ? 0 : record.getWarningCount();
        record.setWarningCount(count + 1);
        examRecordService.updateById(record);
        return Result.success();
    }

    private ExamRecord getOwnedRecordForUpdate(Integer recordId, Integer userId) {
        ExamRecord record = examRecordService.getOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getId, recordId)
                .last("FOR UPDATE"));
        if (record == null || !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException("考试记录不存在");
        }
        return record;
    }

    /**
     * Avoid issuing a range DELETE for a record that has no saved answers.
     * Under MySQL REPEATABLE READ, deleting a missing secondary-index key can
     * take a gap lock and deadlock with concurrent first-time answer inserts.
     * The caller already holds the exam-record row lock, so the existence check
     * and optional delete are safe for this record.
     */
    private void removeRecordAnswersIfPresent(Integer recordId) {
        LambdaQueryWrapper<ExamRecordAnswer> wrapper = new LambdaQueryWrapper<ExamRecordAnswer>()
                .eq(ExamRecordAnswer::getRecordId, recordId);
        if (examRecordAnswerService.count(wrapper) > 0) {
            examRecordAnswerService.remove(wrapper);
        }
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
    @Transactional
    public Result<PageVO<ExamRecord>> myRecords(ExamRecordQueryDTO query) {
        Integer userId = UserContext.getUserId();
        finalizeExpiredRecords(userId);
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
    private Map<Integer, Integer> getPaperQuestionScores(Integer paperId) {
        Map<Integer, Integer> scores = new HashMap<>();
        for (ExamPaperQuestion relation : examPaperQuestionService.list(
                new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paperId))) {
            if (relation.getQuestionId() != null && relation.getPaperScore() != null) {
                scores.put(relation.getQuestionId(), relation.getPaperScore());
            }
        }
        return scores;
    }

    private Map<Integer, Question> getSubmittedQuestions(StudentExamSubmitDTO dto,
                                                          Map<Integer, Integer> scoreByQuestionId) {
        if (dto.getAnswers() == null || dto.getAnswers().isEmpty()) {
            return Map.of();
        }
        Set<Integer> ids = new HashSet<>();
        for (StudentQuestionAnswerDTO answer : dto.getAnswers()) {
            if (!ids.add(answer.getQuestionId())) {
                throw new BusinessException("同一道题不能重复提交");
            }
            if (!scoreByQuestionId.containsKey(answer.getQuestionId())) {
                throw new BusinessException("提交内容包含不属于该试卷的题目");
            }
        }
        Map<Integer, Question> questions = new HashMap<>();
        questionService.listByIds(ids).forEach(question -> questions.put(question.getId(), question));
        if (questions.size() != ids.size()) {
            throw new BusinessException("试卷包含已失效的题目，请联系管理员");
        }
        return questions;
    }

    private void validatePaperAvailability(ExamPaper paper) {
        LocalDateTime now = LocalDateTime.now();
        if (!Objects.equals(paper.getStatus(), 1)
                || paper.getStartTime() != null && now.isBefore(paper.getStartTime())
                || paper.getEndTime() != null && !now.isBefore(paper.getEndTime())) {
            throw new BusinessException("试卷当前不可参加");
        }
    }

    private void validateSubmissionTime(ExamRecord record, ExamPaper paper) {
        if (paper == null || record.getStartTime() == null || paper.getDuration() == null) {
            throw new BusinessException("考试时间配置异常");
        }
        LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
        if (paper.getEndTime() != null && paper.getEndTime().isBefore(deadline)) {
            deadline = paper.getEndTime();
        }
        // 为倒计时归零后的自动交卷与网络传输保留一个很小的宽限窗口。
        if (LocalDateTime.now().isAfter(deadline.plusSeconds(30))) {
            throw new BusinessException("考试时间已结束，无法提交");
        }
    }

    private void finalizeExpiredRecords(Integer userId) {
        List<ExamRecord> activeRecords = examRecordService.list(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getUserId, userId)
                        .eq(ExamRecord::getStatus, 0)
                        .last("FOR UPDATE")
        );
        LocalDateTime now = LocalDateTime.now();
        for (ExamRecord record : activeRecords) {
            ExamPaper paper = examPaperService.getById(record.getPaperId());
            if (paper == null || record.getStartTime() == null || paper.getDuration() == null) {
                continue;
            }
            LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
            if (paper.getEndTime() != null && paper.getEndTime().isBefore(deadline)) {
                deadline = paper.getEndTime();
            }
            if (now.isAfter(deadline.plusSeconds(30))) {
                finalizeFromDraft(record, deadline);
            }
        }
    }

    private void finalizeFromDraft(ExamRecord record, LocalDateTime submitTime) {
        List<ExamRecordAnswer> answers = examRecordAnswerService.list(
                new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, record.getId())
        );
        Set<Integer> questionIds = answers.stream()
                .map(ExamRecordAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Question> questionById = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionService.listByIds(questionIds).forEach(question -> questionById.put(question.getId(), question));
        }
        int totalScore = 0;
        for (ExamRecordAnswer answer : answers) {
            Question question = questionById.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            boolean objective = question.getType() != null && question.getType() != 4;
            boolean correct = objective
                    && normalizeAnswer(question.getAnswer()).equals(normalizeAnswer(answer.getUserAnswer()));
            answer.setCorrectAnswer(question.getAnswer());
            answer.setScore(correct ? answer.getFullScore() : 0);
            answer.setJudgement(objective ? (correct ? "正确" : "错误") : "待批改");
            totalScore += answer.getScore() == null ? 0 : answer.getScore();
            if (objective && !correct) {
                saveWrongQuestion(record.getUserId(), question, answer.getUserAnswer());
            }
        }
        if (!answers.isEmpty()) {
            examRecordAnswerService.updateBatchById(answers);
        }
        record.setScore(totalScore);
        int currentHighest = record.getHighestScore() == null ? 0 : record.getHighestScore();
        record.setHighestScore(Math.max(currentHighest, totalScore));
        record.setStatus(1);
        record.setSubmitTime(submitTime);
        examRecordService.updateById(record);
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
