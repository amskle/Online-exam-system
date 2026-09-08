package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.SubjectQueryDTO;
import com.example.onlineexamsystem.pojo.entity.Subject;
import com.example.onlineexamsystem.pojo.entity.Question;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.SubjectService;
import com.example.onlineexamsystem.service.QuestionService;
import com.example.onlineexamsystem.service.ExamPaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

/**
 * 科目控制器
 */

/**
 * 科目管理控制器
 */
@RestController
@RequestMapping("/subject")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectService subjectService;
    private final QuestionService questionService;
    private final ExamPaperService examPaperService;

    /**
     * 查询全部科目列表
     *
     * @return Result<List<Subject>>
     */
    /**
     * 获取科目列表
     *
     * @return Result<List<Subject>>
     */
    @GetMapping("/list")
    @Auth({2, 3})
    public Result<List<Subject>> list() {
        return Result.success(subjectService.list(new LambdaQueryWrapper<Subject>().orderByDesc(Subject::getCreateTime)));
    }

    /**
     * 分页查询科目列表
     *
     * @return Result<PageVO<Subject>>
     */
    /**
     * 分页查询科目列表
     *
     * @return Result<PageVO<Subject>>
     */
    @GetMapping("/listPage")
    @Auth({2, 3})
    public Result<PageVO<Subject>> listPage(SubjectQueryDTO query) {
        Page<Subject> page = subjectService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Subject>()
                        .like(StringUtils.hasText(query.getName()), Subject::getName, query.getName())
                        .orderByDesc(Subject::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 查询科目详情
     *
     * @return Result<Subject>
     */
    /**
     * 获取科目详情
     *
     * @return Result<Subject>
     */
    @GetMapping("/{id}")
    @Auth({2, 3})
    public Result<Subject> detail(@PathVariable Integer id) {
        return Result.success(subjectService.getById(id));
    }

    /**
     * 新增科目
     *
     * @return Result<Void>
     */
    /**
     * 新增科目
     *
     * @return Result<Void>
     */
    @PostMapping
    @Auth({2, 3})
    public Result<Void> add(@RequestBody Subject subject) {
        validateSubject(subject);
        ensureNameAvailable(subject.getName().trim(), null);
        subject.setName(subject.getName().trim());
        subject.setId(null);
        subject.setCreateTime(LocalDateTime.now());
        subjectService.save(subject);
        return Result.success();
    }

    /**
     * 修改科目
     *
     * @return Result<Void>
     */
    /**
     * 更新科目
     *
     * @return Result<Void>
     */
    @PutMapping
    @Auth({2, 3})
    @Transactional
    public Result<Void> update(@RequestBody Subject subject) {
        if (subject.getId() == null || subjectService.getById(subject.getId()) == null) {
            throw new BusinessException("科目不存在");
        }
        validateSubject(subject);
        String normalizedName = subject.getName().trim();
        ensureNameAvailable(normalizedName, subject.getId());
        subject.setName(normalizedName);
        subject.setCreateTime(null);
        if (!subjectService.updateById(subject)) {
            throw new BusinessException("科目更新失败");
        }
        questionService.update(new LambdaUpdateWrapper<Question>()
                .eq(Question::getSubjectId, subject.getId())
                .set(Question::getSubjectName, normalizedName));
        examPaperService.update(new LambdaUpdateWrapper<ExamPaper>()
                .eq(ExamPaper::getSubjectId, subject.getId())
                .set(ExamPaper::getSubjectName, normalizedName));
        return Result.success();
    }

    /**
     * 删除科目
     *
     * @return Result<Void>
     */
    /**
     * 删除科目
     *
     * @return Result<Void>
     */
    @DeleteMapping("/{id}")
    @Auth({2, 3})
    public Result<Void> delete(@PathVariable Integer id) {
        if (questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getSubjectId, id)) > 0
                || examPaperService.count(new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getSubjectId, id)) > 0) {
            throw new BusinessException("科目已被题目或试卷引用，不能删除");
        }
        if (!subjectService.removeById(id)) {
            throw new BusinessException("科目不存在");
        }
        return Result.success();
    }

    private void validateSubject(Subject subject) {
        if (subject == null || !StringUtils.hasText(subject.getName())
                || subject.getName().trim().length() > 50
                || (subject.getDescription() != null && subject.getDescription().length() > 200)) {
            throw new BusinessException("科目名称或描述不正确");
        }
    }

    private void ensureNameAvailable(String name, Integer allowedId) {
        long duplicates = subjectService.count(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getName, name)
                .ne(allowedId != null, Subject::getId, allowedId));
        if (duplicates > 0) {
            throw new BusinessException("科目名称已存在");
        }
    }
}
