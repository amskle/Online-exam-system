package com.example.onlineexamsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.onlineexamsystem.pojo.dto.AutoGeneratePaperDTO;
import com.example.onlineexamsystem.pojo.dto.ExamPaperSaveDTO;
import com.example.onlineexamsystem.pojo.entity.ExamPaper;
import com.example.onlineexamsystem.pojo.vo.ExamPaperDetailVO;

/**
 * 试卷服务接口
 */
public interface ExamPaperService extends IService<ExamPaper> {
    /**
     * 保存试卷（含题目关联）
     *
     * @param dto 试卷保存参数对象
     */
    void savePaper(ExamPaperSaveDTO dto);

    /**
     * 修改试卷（重建题目关联）
     *
     * @param dto 试卷保存参数对象
     */
    void updatePaper(ExamPaperSaveDTO dto);

    /**
     * 查询试卷详情（含题目列表）
     *
     * @param id 试卷id
     * @return ExamPaperDetailVO
     */
    ExamPaperDetailVO detail(Integer id);

    /**
     * 按题型难度配置自动组卷
     *
     * @param dto 自动组卷参数对象
     */
    void autoGenerate(AutoGeneratePaperDTO dto);
}
