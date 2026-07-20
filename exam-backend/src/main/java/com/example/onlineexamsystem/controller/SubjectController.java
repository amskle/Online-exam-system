package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.common.exception.BusinessException;
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
    @Auth(3)
    public Result<Void> update(@RequestBody Subject subject) {
        subjectService.updateById(subject);
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
    @Auth(3)
    public Result<Void> delete(@PathVariable Integer id) {
        subjectService.removeById(id);
        return Result.success();
    }
}
