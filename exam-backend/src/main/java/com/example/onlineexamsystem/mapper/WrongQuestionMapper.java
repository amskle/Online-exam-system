package com.example.onlineexamsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.onlineexamsystem.pojo.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 错题 Mapper 接口
 */
@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {
    @Update("""
            <script>
            UPDATE wrong_question SET wrong_count = wrong_count + 1, mastered = false,
            user_answer = CASE id
            <foreach collection="items" item="w">WHEN #{w.id} THEN #{w.userAnswer}</foreach>
            ELSE user_answer END,
            correct_answer = CASE id
            <foreach collection="items" item="w">WHEN #{w.id} THEN #{w.correctAnswer}</foreach>
            ELSE correct_answer END,
            last_wrong_time = CASE id
            <foreach collection="items" item="w">WHEN #{w.id} THEN #{w.lastWrongTime}</foreach>
            ELSE last_wrong_time END
            WHERE id IN
            <foreach collection="items" item="w" open="(" close=")" separator=",">
              #{w.id}
            </foreach>
            </script>
            """)
    int incrementBatch(@Param("items") List<WrongQuestion> items);
}
