package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.mapper.ExamRecordMapper;
import com.example.onlineexamsystem.pojo.dto.ExamRecordGradeDTO;
import com.example.onlineexamsystem.pojo.dto.GradeAnswerDTO;
import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import com.example.onlineexamsystem.pojo.entity.ExamRecordAnswer;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;
import com.example.onlineexamsystem.service.ExamRecordAnswerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExamRecordServiceImpl 单元测试 — 覆盖详情查询、主观题批改
 */
@ExtendWith(MockitoExtension.class)
class ExamRecordServiceImplTest {

    @Mock private ExamRecordMapper recordMapper;
    @Mock private ExamRecordAnswerService answerService;

    private ExamRecordServiceImpl recordService;

    @BeforeEach
    void setUp() {
        recordService = new ExamRecordServiceImpl(answerService);
        ReflectionTestUtils.setField(recordService, "baseMapper", recordMapper);
    }

    // ── detail ──

    @Test
    void detail_shouldReturnRecordWithAnswers_whenRecordExists() {
        ExamRecord record = new ExamRecord();
        record.setId(1);
        record.setScore(85);

        ExamRecordAnswer answer = new ExamRecordAnswer();
        answer.setId(10);
        answer.setRecordId(1);
        answer.setScore(10);

        when(recordMapper.selectById(1)).thenReturn(record);
        when(answerService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(answer));

        ExamRecordDetailVO result = recordService.detail(1);

        assertNotNull(result);
        assertEquals(1, result.getAnswers().size());
        assertEquals(10, result.getAnswers().get(0).getId());
    }

    @Test
    void detail_shouldThrow_whenRecordNotFound() {
        when(recordMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> recordService.detail(999));
        assertEquals("考试记录不存在", ex.getMessage());
    }

    // ── grade ──

    @Test
    void grade_shouldUpdateScoresAndRecalculateTotal() {
        GradeAnswerDTO ans1 = new GradeAnswerDTO();
        ans1.setAnswerId(101);
        ans1.setScore(8);
        ans1.setJudgement("回答基本正确");

        GradeAnswerDTO ans2 = new GradeAnswerDTO();
        ans2.setAnswerId(102);
        ans2.setScore(15);
        ans2.setJudgement("回答完全正确");

        ExamRecordGradeDTO dto = new ExamRecordGradeDTO();
        dto.setRecordId(1);
        dto.setAnswers(List.of(ans1, ans2));

        ExamRecordAnswer updated1 = new ExamRecordAnswer();
        updated1.setId(101);
        updated1.setScore(8);
        updated1.setRecordId(1);

        ExamRecordAnswer updated2 = new ExamRecordAnswer();
        updated2.setId(102);
        updated2.setScore(15);
        updated2.setRecordId(1);

        when(answerService.updateById(any())).thenReturn(true);
        when(answerService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(updated1, updated2));

        assertDoesNotThrow(() -> recordService.grade(dto));

        verify(answerService, times(2)).updateById(any());
        verify(recordMapper, times(1)).updateById((ExamRecord) any());
    }

    @Test
    void grade_shouldThrow_whenParamsNull() {
        ExamRecordGradeDTO dto = new ExamRecordGradeDTO();

        BusinessException ex = assertThrows(BusinessException.class, () -> recordService.grade(dto));
        assertEquals("批改参数不能为空", ex.getMessage());
        verify(answerService, never()).updateById(any());
    }

    @Test
    void grade_shouldHandleZeroTotalScore() {
        GradeAnswerDTO ans = new GradeAnswerDTO();
        ans.setAnswerId(101);
        ans.setScore(0);
        ans.setJudgement("回答错误");

        ExamRecordGradeDTO dto = new ExamRecordGradeDTO();
        dto.setRecordId(1);
        dto.setAnswers(List.of(ans));

        ExamRecordAnswer updated = new ExamRecordAnswer();
        updated.setId(101);
        updated.setScore(0);
        updated.setRecordId(1);

        when(answerService.updateById(any())).thenReturn(true);
        when(answerService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(updated));

        assertDoesNotThrow(() -> recordService.grade(dto));
        verify(recordMapper, times(1)).updateById((ExamRecord) any());
    }
}
