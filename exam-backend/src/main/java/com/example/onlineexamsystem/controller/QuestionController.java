package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.QuestionQueryDTO;
import com.example.onlineexamsystem.pojo.entity.Question;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
@Auth({2, 3})
public class QuestionController {
    private final QuestionService questionService;

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

    @GetMapping("/{id}")
    public Result<Question> detail(@PathVariable Integer id) {
        return Result.success(questionService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody Question question) {
        if (question.getSubjectId() == null || question.getType() == null || question.getDifficulty() == null) {
            throw new BusinessException("科目、题型和难度不能为空");
        }
        question.setId(null);
        question.setCreateTime(LocalDateTime.now());
        questionService.save(question);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Question question) {
        questionService.updateById(question);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        questionService.removeById(id);
        return Result.success();
    }
}
