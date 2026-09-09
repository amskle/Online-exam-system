package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.onlineexamsystem.mapper.WrongQuestionMapper;
import com.example.onlineexamsystem.pojo.entity.WrongQuestion;
import com.example.onlineexamsystem.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 错题服务实现类
 */
@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion> implements WrongQuestionService {
    @Override
    @Transactional
    public void recordWrongAnswers(List<WrongQuestion> answers) {
        if (answers.stream().map(WrongQuestion::getUserId).distinct().count() > 1) {
            throw new IllegalArgumentException("A wrong-answer batch must belong to one user");
        }
        // Caller holds the user's row lock before exam-record locks. This serializes
        // overlapping papers for one user without blocking different students.
        List<WrongQuestion> ordered = answers.stream()
                .sorted(Comparator.comparing(WrongQuestion::getUserId)
                        .thenComparing(WrongQuestion::getQuestionId)).toList();
        for (int offset = 0; offset < ordered.size(); offset += 200) {
            List<WrongQuestion> chunk = ordered.subList(offset, Math.min(offset + 200, ordered.size()));
            Map<Integer, Integer> existingIds = list(new LambdaQueryWrapper<WrongQuestion>()
                    .select(WrongQuestion::getId, WrongQuestion::getQuestionId)
                    .eq(WrongQuestion::getUserId, chunk.getFirst().getUserId())
                    .in(WrongQuestion::getQuestionId, chunk.stream().map(WrongQuestion::getQuestionId).toList()))
                    .stream().collect(Collectors.toMap(WrongQuestion::getQuestionId, WrongQuestion::getId));
            List<WrongQuestion> inserts = new ArrayList<>();
            List<WrongQuestion> updates = new ArrayList<>();
            for (WrongQuestion answer : chunk) {
                Integer id = existingIds.get(answer.getQuestionId());
                if (id == null) {
                    inserts.add(answer);
                } else {
                    answer.setId(id);
                    updates.add(answer);
                }
            }
            if (!inserts.isEmpty()) {
                saveBatch(inserts);
            }
            if (!updates.isEmpty()) {
                updates.sort(Comparator.comparing(WrongQuestion::getId));
                baseMapper.incrementBatch(updates);
            }
        }
    }
}
