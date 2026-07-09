package com.example.onlineexamsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.onlineexamsystem.annotation.Auth;
import com.example.onlineexamsystem.pojo.api.Result;
import com.example.onlineexamsystem.pojo.entity.*;
import com.example.onlineexamsystem.pojo.vo.DashboardOverviewVO;
import com.example.onlineexamsystem.pojo.vo.NameValueVO;
import com.example.onlineexamsystem.pojo.vo.StudentScoreStatsVO;
import com.example.onlineexamsystem.pojo.vo.TrendStatsVO;
import com.example.onlineexamsystem.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                buildTrendStats()
        ));
    }

    @GetMapping("/trends")
    public Result<List<TrendStatsVO>> trends(Integer days) {
        return Result.success(buildTrendStats());
    }

    @GetMapping("/score-stats")
    public Result<StudentScoreStatsVO> scoreStats() {
        return Result.success(buildScoreStats());
    }

    private StudentScoreStatsVO buildScoreStats() {
        List<ExamRecord> records = examRecordService.list(
                new LambdaQueryWrapper<ExamRecord>()
                        .isNotNull(ExamRecord::getScore)
                        .isNotNull(ExamRecord::getTotalScore)
                        .gt(ExamRecord::getTotalScore, 0)
        );

        if (records.isEmpty()) {
            return new StudentScoreStatsVO(
                    0L,
                    0D,
                    0L,
                    0L,
                    0L,
                    0D,
                    buildScoreDistribution(new long[5]),
                    List.of()
            );
        }

        long[] distribution = new long[5];
        long passCount = 0;
        double totalRate = 0D;
        double highestRate = 0D;
        double lowestRate = 100D;
        Map<String, StudentScoreBucket> studentScores = new HashMap<>();

        for (ExamRecord record : records) {
            double rate = normalizeScoreRate(record);
            totalRate += rate;
            highestRate = Math.max(highestRate, rate);
            lowestRate = Math.min(lowestRate, rate);
            distribution[scoreRangeIndex(rate)]++;

            if (isPassed(record, rate)) {
                passCount++;
            }

            String studentKey = record.getUserId() == null ? record.getUsername() : String.valueOf(record.getUserId());
            StudentScoreBucket bucket = studentScores.computeIfAbsent(
                    studentKey == null ? "unknown" : studentKey,
                    key -> new StudentScoreBucket(displayStudentName(record.getUsername()))
            );
            bucket.add(rate);
        }

        List<NameValueVO> topStudentScores = studentScores.values().stream()
                .sorted(Comparator.comparingDouble(StudentScoreBucket::average).reversed())
                .limit(8)
                .map(bucket -> new NameValueVO(bucket.getName(), Math.round(bucket.average())))
                .toList();

        long recordCount = records.size();
        return new StudentScoreStatsVO(
                recordCount,
                roundOne(totalRate / recordCount),
                Math.round(highestRate),
                Math.round(lowestRate),
                passCount,
                roundOne(passCount * 100D / recordCount),
                buildScoreDistribution(distribution),
                topStudentScores
        );
    }

    private List<NameValueVO> buildScoreDistribution(long[] distribution) {
        return List.of(
                new NameValueVO("60分以下", distribution[0]),
                new NameValueVO("60-69分", distribution[1]),
                new NameValueVO("70-79分", distribution[2]),
                new NameValueVO("80-89分", distribution[3]),
                new NameValueVO("90-100分", distribution[4])
        );
    }

    private double normalizeScoreRate(ExamRecord record) {
        double rate = record.getScore() * 100D / record.getTotalScore();
        return Math.max(0D, Math.min(100D, rate));
    }

    private int scoreRangeIndex(double rate) {
        if (rate < 60D) {
            return 0;
        }
        if (rate < 70D) {
            return 1;
        }
        if (rate < 80D) {
            return 2;
        }
        if (rate < 90D) {
            return 3;
        }
        return 4;
    }

    private boolean isPassed(ExamRecord record, double rate) {
        if (record.getPassScore() != null) {
            return record.getScore() >= record.getPassScore();
        }
        return rate >= 60D;
    }

    private double roundOne(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private String displayStudentName(String username) {
        return username == null || username.isBlank() ? "未命名学生" : username;
    }

    private List<TrendStatsVO> buildTrendStats() {
        List<TrendStatsVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate startDate = findFirstUserCreateDate(today);
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, today) + 1;
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
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

    private LocalDate findFirstUserCreateDate(LocalDate fallbackDate) {
        BaseUser firstUser = baseUserService.getOne(
                new LambdaQueryWrapper<BaseUser>()
                        .isNotNull(BaseUser::getCreateTime)
                        .orderByAsc(BaseUser::getCreateTime)
                        .last("limit 1")
        );
        if (firstUser == null || firstUser.getCreateTime() == null) {
            return fallbackDate;
        }
        return firstUser.getCreateTime().toLocalDate();
    }

    private static class StudentScoreBucket {
        private final String name;
        private double total;
        private long count;

        private StudentScoreBucket(String name) {
            this.name = name;
        }

        private void add(double scoreRate) {
            total += scoreRate;
            count++;
        }

        private double average() {
            return count == 0 ? 0D : total / count;
        }

        private String getName() {
            return name;
        }
    }
}
