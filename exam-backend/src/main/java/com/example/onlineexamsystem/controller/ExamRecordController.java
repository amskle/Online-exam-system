package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.dto.ExamRecordGradeDTO;
import com.example.onlineexamsystem.pojo.dto.ExamRecordQueryDTO;
import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import com.example.onlineexamsystem.pojo.entity.ExamRecordAnswer;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;
import com.example.onlineexamsystem.pojo.vo.PageVO;
import com.example.onlineexamsystem.service.ExamRecordAnswerService;
import com.example.onlineexamsystem.service.ExamRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 考试记录控制器
 */
/**
 * 考试记录控制器（管理员/教师）
 */
@RestController
@RequestMapping("/examRecord")
@RequiredArgsConstructor
@Auth({2, 3})
public class ExamRecordController {
    private final ExamRecordService examRecordService;
    private final ExamRecordAnswerService examRecordAnswerService;

    /**
     * 分页查询考试记录列表
     *
     * @return Result<PageVO<ExamRecord>>
     */
    /**
     * 分页查询考试记录
     *
     * @return Result<PageVO<ExamRecord>>
     */
    @GetMapping("/listPage")
    public Result<PageVO<ExamRecord>> listPage(ExamRecordQueryDTO query) {
        Page<ExamRecord> page = examRecordService.page(
                Page.of(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<ExamRecord>()
                        .like(StringUtils.hasText(query.getPaperTitle()), ExamRecord::getPaperTitle, query.getPaperTitle())
                        .like(StringUtils.hasText(query.getUsername()), ExamRecord::getUsername, query.getUsername())
                        .eq(query.getStatus() != null, ExamRecord::getStatus, query.getStatus())
                        .orderByDesc(ExamRecord::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 查询考试记录详情（含答题明细）
     *
     * @return Result<ExamRecordDetailVO>
     */
    /**
     * 获取考试记录详情（含答题内容）
     *
     * @return Result<ExamRecordDetailVO>
     */
    @GetMapping("/{id}/detail")
    public Result<ExamRecordDetailVO> detail(@PathVariable Integer id) {
        return Result.success(examRecordService.detail(id));
    }

    /**
     * 批改主观题并汇总得分
     *
     * @return Result<Void>
     */
    /**
     * 批改主观题
     *
     * @return Result<Void>
     */
    @PostMapping("/grade")
    public Result<Void> grade(@RequestBody ExamRecordGradeDTO dto) {
        examRecordService.grade(dto);
        return Result.success();
    }

    /**
     * 删除考试记录（同时清理答题明细）
     *
     * @return Result<Void>
     */
    /**
     * 删除考试记录
     *
     * @return Result<Void>
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        examRecordAnswerService.remove(new LambdaQueryWrapper<ExamRecordAnswer>().eq(ExamRecordAnswer::getRecordId, id));
        examRecordService.removeById(id);
        return Result.success();
    }
}
