package com.system;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(classes = com.example.onlineexamsystem.OnlineExamSystemApplication.class)
public class OnlineExamSystemApplicationTests {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testString() {
        redisTemplate.opsForValue().set("name", "哥哥");
        String name = redisTemplate.opsForValue().get("name");
        System.out.println(name);
    }


}
