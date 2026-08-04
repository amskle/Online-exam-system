package com.example.onlineexamsystem.service.impl;

import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.dto.EmailSendDTO;
import com.example.onlineexamsystem.service.BaseUserService;
import com.example.onlineexamsystem.utils.EmailUtil;
import com.example.onlineexamsystem.utils.JwtUtil;
import com.example.onlineexamsystem.utils.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock private BaseUserService baseUserService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private RedisUtil redisUtil;
    @Mock private EmailUtil emailUtil;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(baseUserService, passwordEncoder, jwtUtil, redisUtil, emailUtil);
        ReflectionTestUtils.setField(emailService, "codeTtl", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(emailService, "sendCooldown", Duration.ofSeconds(60));
        ReflectionTestUtils.setField(emailService, "dailyLimit", 10);
        ReflectionTestUtils.setField(emailService, "trustedDeviceTtl", Duration.ofDays(7));
        ReflectionTestUtils.setField(emailService, "secureCookie", false);
    }

    @Test
    void sendCode_shouldCleanupChallengeAndRateLimit_whenMailSendFails() {
        EmailSendDTO dto = new EmailSendDTO();
        dto.setChallengeId("challenge-1");

        Map<String, String> challenge = new HashMap<>();
        challenge.put("purpose", "LOGIN");
        challenge.put("email", "test@example.com");
        challenge.put("userId", "1");
        challenge.put("state", "CODE_SENT");

        when(redisUtil.getHash("auth:challenge:challenge-1")).thenReturn(challenge);
        when(redisUtil.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(redisUtil.increment(anyString(), any(Duration.class))).thenReturn(1L);
        doThrow(new RuntimeException("SMTP auth failed"))
                .when(emailUtil).sendVerificationCode(eq("test@example.com"), anyString(), eq("LOGIN"));

        BusinessException ex = assertThrows(BusinessException.class, () -> emailService.sendCode(dto));

        assertEquals("验证码邮件发送失败，请检查邮件配置后重试", ex.getMessage());
        verify(redisUtil).delete("auth:challenge:challenge-1");
        verify(redisUtil).delete(argThat(key -> key.startsWith("auth:email:cooldown:")));
        verify(redisUtil).decrementIfExists(argThat(key -> key.startsWith("auth:email:daily:")));
    }
}
