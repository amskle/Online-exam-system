//package com.example.onlineexamsystem.controller;
//
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.example.onlineexamsystem.annotation.Auth;
//import com.example.onlineexamsystem.common.exception.BusinessException;
//import com.example.onlineexamsystem.pojo.api.Result;
//import com.example.onlineexamsystem.pojo.dto.ExamPaperQueryDTO;
//import com.example.onlineexamsystem.pojo.entity.ExamPaper;
//import com.example.onlineexamsystem.pojo.vo.PageVO;
//import com.example.onlineexamsystem.service.ExamPaperService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.util.StringUtils;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//
//@RestController
//@RequestMapping("/examPaper")
//@RequiredArgsConstructor
//@Auth({2, 3})
//public class ExamPaperSimpleController {
//    private final ExamPaperService examPaperService;
//
//    @GetMapping("/listPage")
//    public Result<PageVO<ExamPaper>> listPage(ExamPaperQueryDTO query) {
//        Page<ExamPaper> page = examPaperService.page(
//                Page.of(query.getPageNum(), query.getPageSize()),
//                new LambdaQueryWrapper<ExamPaper>()
//                        .like(StringUtils.hasText(query.getTitle()), ExamPaper::getTitle, query.getTitle())
//                        .orderByDesc(ExamPaper::getCreateTime)
//        );
//        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
//    }
//
//    @GetMapping("/{id}")
//    public Result<ExamPaper> detail(@PathVariable Integer id) {
//        return Result.success(examPaperService.getById(id));
//    }
//
//    @PostMapping
//    public Result<Void> add(@RequestBody ExamPaper paper) {
//        if (!StringUtils.hasText(paper.getTitle())) {
//            throw new BusinessException("试卷标题不能为空");
//        }
//        if (paper.getSubjectId() == null) {
//            throw new BusinessException("请选择科目");
//        }
//        paper.setId(null);
//        paper.setStatus(0);
//        paper.setCreateTime(LocalDateTime.now());
//        examPaperService.save(paper);
//        return Result.success();
//    }
//
//    @DeleteMapping("/{id}")
//    public Result<Void> delete(@PathVariable Integer id) {
//        examPaperService.removeById(id);
//        return Result.success();
//    }
//}
