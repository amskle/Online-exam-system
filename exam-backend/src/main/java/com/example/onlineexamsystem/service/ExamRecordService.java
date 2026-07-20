package com.example.onlineexamsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.ExamRecordGradeDTO;
import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;

/**
 * 考试记录服务接口
 */
public interface ExamRecordService extends IService<ExamRecord> {
    /**
     * 查询考试记录详情（含答题明细）
     *
     * @param id 考试记录id
     * @return ExamRecordDetailVO
     */
    ExamRecordDetailVO detail(Integer id);

    /**
     * 批改主观题并汇总得分
     *
     * @param dto 批改参数对象
     */
    void grade(ExamRecordGradeDTO dto);
}
