package com.example.onlineexamsystem.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {
    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -2
            end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
                return -1
            end
            local expected = redis.call('HGET', KEYS[1], 'codeHash')
            if expected ~= ARGV[1] then
                attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
                if attempts >= tonumber(ARGV[2]) then
                    redis.call('DEL', KEYS[1])
                    return -1
                end
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void putHash(String key, Map<String, String> values, Duration ttl) {
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttl);
    }

    public Map<String, String> getHash(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        entries.forEach((field, value) -> result.put(String.valueOf(field), String.valueOf(value)));
        return result;
    }

    public void updateChallengeCode(String key, String email, String codeHash, Duration ttl) {
        Map<String, String> fields = new HashMap<>();
        fields.put("email", email);
        fields.put("codeHash", codeHash);
        fields.put("attempts", "0");
        fields.put("state", "CODE_SENT");
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, ttl);
    }

    public long verifyAndDeleteCode(String key, String codeHash, int maxAttempts) {
        Long result = redisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                Collections.singletonList(key),
                codeHash,
                String.valueOf(maxAttempts)
        );
        return result == null ? -2 : result;
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    public long increment(String key, Duration ttlWhenCreated) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            redisTemplate.expire(key, ttlWhenCreated);
        }
        return value == null ? 0 : value;
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public long getExpireSeconds(String key) {
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return seconds == null || seconds < 0 ? 0 : seconds;
    }
}
