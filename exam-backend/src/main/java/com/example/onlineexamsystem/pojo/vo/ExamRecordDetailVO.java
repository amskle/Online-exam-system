package com.example.onlineexamsystem.pojo.vo;

import com.example.onlineexamsystem.pojo.entity.ExamRecord;
import com.example.onlineexamsystem.pojo.entity.ExamRecordAnswer;
import lombok.Data;

import java.util.List;

@Data
public class ExamRecordDetailVO extends ExamRecord {
    private List<ExamRecordAnswer> answers;
}
