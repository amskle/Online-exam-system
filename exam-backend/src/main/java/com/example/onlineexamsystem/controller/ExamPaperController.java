package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.AutoGeneratePaperDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperQueryDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperSaveDTO;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import com.example.onlineexamsystem.pojo.entity.ExamPaperQuestion;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.ExamPaperQuestionService;
import com.example.onlineexamsystem.service.ExamPaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/examPaper")
@RequiredArgsConstructor
@Auth({2, 3})
public class ExamPaperController {
    private final ExamPaperService examPaperService;
    private final ExamPaperQuestionService examPaperQuestionService;

    @GetMapping("/listPage")
    public Result<PageVO<ExamPaper>> listPage(ExamPaperQueryDTO query) {
        Page<ExamPaper> page = examPaperService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<ExamPaper>()
                        .like(StringUtils.hasText(query.getTitle()), ExamPaper::getTitle, query.getTitle())
                        .eq(query.getSubjectId() != null, ExamPaper::getSubjectId, query.getSubjectId())
                        .eq(query.getStatus() != null, ExamPaper::getStatus, query.getStatus())
                        .orderByDesc(ExamPaper::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/{id}/detail")
    public Result<ExamPaperDetailVO> detail(@PathVariable Integer id) {
        return Result.success(examPaperService.detail(id));
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody ExamPaperSaveDTO dto) {
        examPaperService.savePaper(dto);
        return Result.success();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody ExamPaperSaveDTO dto) {
        examPaperService.updatePaper(dto);
        return Result.success();
    }

    @PostMapping("/autoGenerate")
    public Result<Void> autoGenerate(@RequestBody AutoGeneratePaperDTO dto) {
        examPaperService.autoGenerate(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        examPaperService.removeById(id);
        examPaperQuestionService.remove(new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, id));
        return Result.success();
    }
}
