package com.example.onlineexamsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.AutoGeneratePaperDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperSaveDTO;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;

public interface ExamPaperService extends IService<ExamPaper> {
    void savePaper(ExamPaperSaveDTO dto);
    void updatePaper(ExamPaperSaveDTO dto);
    ExamPaperDetailVO detail(Integer id);
    void autoGenerate(AutoGeneratePaperDTO dto);
}
