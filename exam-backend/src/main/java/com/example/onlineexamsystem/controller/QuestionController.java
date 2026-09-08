package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.QuestionQueryDTO;
import com.example.onlineexamsystem.pojo.entity.Question;
import com.example.onlineexamsystem.pojo.entity.Subject;
import com.example.onlineexamsystem.pojo.entity.ExamPaperQuestion;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.QuestionService;
import com.example.onlineexamsystem.service.SubjectService;
import com.example.onlineexamsystem.service.ExamPaperQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

/**
 * 题目控制器
 */
/**
 * 题目管理控制器
 */
@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
@Auth({2, 3})
public class QuestionController {
    private final QuestionService questionService;
    private final SubjectService subjectService;
    private final ExamPaperQuestionService examPaperQuestionService;

    /**
     * 分页查询题目列表
     *
     * @return Result<PageVO<Question>>
     */
    /**
     * 分页查询题目列表
     *
     * @return Result<PageVO<Question>>
     */
    @GetMapping("/listPage")
    public Result<PageVO<Question>> listPage(QuestionQueryDTO query) {
        Page<Question> page = questionService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Question>()
                        .eq(query.getSubjectId() != null, Question::getSubjectId, query.getSubjectId())
                        .eq(query.getType() != null, Question::getType, query.getType())
                        .eq(query.getDifficulty() != null, Question::getDifficulty, query.getDifficulty())
                        .orderByDesc(Question::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 查询题目详情
     *
     * @return Result<Question>
     */
    /**
     * 获取题目详情
     *
     * @return Result<Question>
     */
    @GetMapping("/{id}")
    public Result<Question> detail(@PathVariable Integer id) {
        return Result.success(questionService.getById(id));
    }

    /**
     * 新增题目
     *
     * @return Result<Void>
     */
    /**
     * 新增题目
     *
     * @return Result<Void>
     */
    @PostMapping
    public Result<Void> add(@RequestBody Question question) {
        validateAndNormalize(question);
        question.setId(null);
        question.setCreateTime(LocalDateTime.now());
        questionService.save(question);
        return Result.success();
    }

    /**
     * 修改题目
     *
     * @return Result<Void>
     */
    /**
     * 更新题目
     *
     * @return Result<Void>
     */
    @PutMapping
    public Result<Void> update(@RequestBody Question question) {
        if (question.getId() == null || questionService.getById(question.getId()) == null) {
            throw new BusinessException("题目不存在");
        }
        ensureQuestionNotReferenced(question.getId());
        validateAndNormalize(question);
        question.setCreateTime(null);
        if (!questionService.updateById(question)) {
            throw new BusinessException("题目更新失败");
        }
        return Result.success();
    }

    /**
     * 删除题目
     *
     * @return Result<Void>
     */
    /**
     * 删除题目
     *
     * @return Result<Void>
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        ensureQuestionNotReferenced(id);
        if (!questionService.removeById(id)) {
            throw new BusinessException("题目不存在");
        }
        return Result.success();
    }

    private void validateAndNormalize(Question question) {
        if (question == null || question.getSubjectId() == null
                || question.getType() == null || question.getType() < 1 || question.getType() > 4
                || question.getDifficulty() == null || question.getDifficulty() < 1 || question.getDifficulty() > 3
                || !StringUtils.hasText(question.getContent()) || question.getContent().length() > 5000
                || !StringUtils.hasText(question.getAnswer()) || question.getAnswer().length() > 5000
                || question.getScore() == null || question.getScore() <= 0 || question.getScore() > 1000) {
            throw new BusinessException("题目的科目、题型、难度、内容、答案或分值配置不正确");
        }
        if ((question.getType() == 1 || question.getType() == 2) && !StringUtils.hasText(question.getOptions())) {
            throw new BusinessException("选择题必须配置选项");
        }
        Subject subject = subjectService.getById(question.getSubjectId());
        if (subject == null) {
            throw new BusinessException("科目不存在");
        }
        question.setSubjectName(subject.getName());
    }

    private void ensureQuestionNotReferenced(Integer questionId) {
        if (examPaperQuestionService.count(new LambdaQueryWrapper<ExamPaperQuestion>()
                .eq(ExamPaperQuestion::getQuestionId, questionId)) > 0) {
            throw new BusinessException("题目已被试卷引用，不能修改或删除");
        }
    }
}
