package com.example.onlineexamsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.ExamRecordGradeDTO;
import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import com.example.onlineexamsystem.pojo.vo.ExamRecordDetailVO;

public interface ExamRecordService extends IService<ExamRecord> {
    ExamRecordDetailVO detail(Integer id);
    void grade(ExamRecordGradeDTO dto);
}
