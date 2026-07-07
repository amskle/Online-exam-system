package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.entity.*;
import com.example.onlineexamsystem.pojo.vo.DashboardOverviewVO;
import com.example.onlineexamsystem.pojo.vo.NameValueVO;
import com.example.onlineexamsystem.pojo.vo.TrendStatsVO;
import com.example.onlineexamsystem.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Auth(3)
public class DashboardController {
    private final BaseUserService baseUserService;
    private final ExamPaperService examPaperService;
    private final ExamRecordService examRecordService;
    private final QuestionService questionService;
    private final SubjectService subjectService;

    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        Long userCount = baseUserService.count(new LambdaQueryWrapper<BaseUser>().in(BaseUser::getRole, 1, 2));
        Long adminCount = baseUserService.count(new LambdaQueryWrapper<BaseUser>().eq(BaseUser::getRole, 3));
        Long paperCount = examPaperService.count();
        Long recordCount = examRecordService.count();
        Long questionCount = questionService.count();
        Long subjectCount = subjectService.count();

        List<NameValueVO> questionTypeStats = List.of(
                new NameValueVO("单选题", questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getType, 1))),
                new NameValueVO("多选题", questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getType, 2))),
                new NameValueVO("判断题", questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getType, 3))),
                new NameValueVO("主观题", questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getType, 4)))
        );

        List<NameValueVO> subjectQuestionStats = new ArrayList<>();
        for (Subject subject : subjectService.list()) {
            Long count = questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getSubjectId, subject.getId()));
            subjectQuestionStats.add(new NameValueVO(subject.getName(), count));
        }

        return Result.success(new DashboardOverviewVO(
                userCount,
                adminCount,
                paperCount,
                recordCount,
                questionCount,
                subjectCount,
                questionTypeStats,
                subjectQuestionStats,
                buildTrendStats(7)
        ));
    }

    @GetMapping("/trends")
    public Result<List<TrendStatsVO>> trends(Integer days) {
        int safeDays = days == null ? 7 : Math.max(1, Math.min(days, 30));
        return Result.success(buildTrendStats(safeDays));
    }

    private List<TrendStatsVO> buildTrendStats(int days) {
        List<TrendStatsVO> result = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < days; i++) {
            java.time.LocalDate date = today.plusDays(i);
            java.time.LocalDateTime start = date.atStartOfDay();
            java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
            Long users = baseUserService.count(
                    new LambdaQueryWrapper<BaseUser>()
                            .ge(BaseUser::getCreateTime, start)
                            .lt(BaseUser::getCreateTime, end)
            );
            Long exams = examRecordService.count(
                    new LambdaQueryWrapper<ExamRecord>()
                            .ge(ExamRecord::getCreateTime, start)
                            .lt(ExamRecord::getCreateTime, end)
            );
            Long questions = questionService.count(
                    new LambdaQueryWrapper<Question>()
                            .ge(Question::getCreateTime, start)
                            .lt(Question::getCreateTime, end)
            );
            result.add(new TrendStatsVO(date.toString(), users, exams, questions));
        }
        return result;
    }
}
