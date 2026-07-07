package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.exception.BusinessException;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.SubjectQueryDTO;
import com.example.onlineexamsystem.pojo.entity.Subject;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/subject")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectService subjectService;

    @GetMapping("/list")
    @Auth({2, 3})
    public Result<List<Subject>> list() {
        return Result.success(subjectService.list(new LambdaQueryWrapper<Subject>().orderByDesc(Subject::getCreateTime)));
    }

    @GetMapping("/listPage")
    @Auth(3)
    public Result<PageVO<Subject>> listPage(SubjectQueryDTO query) {
        Page<Subject> page = subjectService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Subject>()
                        .like(StringUtils.hasText(query.getName()), Subject::getName, query.getName())
                        .orderByDesc(Subject::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/{id}")
    @Auth({2, 3})
    public Result<Subject> detail(@PathVariable Integer id) {
        return Result.success(subjectService.getById(id));
    }

    @PostMapping
    @Auth(3)
    public Result<Void> add(@RequestBody Subject subject) {
        if (!StringUtils.hasText(subject.getName())) {
            throw new BusinessException("科目名称不能为空");
        }
        subject.setId(null);
        subject.setCreateTime(LocalDateTime.now());
        subjectService.save(subject);
        return Result.success();
    }

    @PutMapping
    @Auth(3)
    public Result<Void> update(@RequestBody Subject subject) {
        subjectService.updateById(subject);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Auth(3)
    public Result<Void> delete(@PathVariable Integer id) {
        subjectService.removeById(id);
        return Result.success();
    }
}
