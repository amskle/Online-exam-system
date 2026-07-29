package com.example.onlineexamsystem.service.impl;

import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.mapper.BaseUserMapper;
import com.example.onlineexamsystem.pojo.dto.UserUpdatePasswordDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.enums.RoleEnum;
import com.example.onlineexamsystem.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BaseUserServiceImpl 单元测试 — 覆盖 token验证、修改密码
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class BaseUserServiceImplTest {

    @Mock private BaseUserMapper baseMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.example.onlineexamsystem.service.FileUploadService fileUploadService;

    private BaseUserServiceImpl userService;

    private BaseUser testUser;

    @BeforeEach
    void setUp() {
        userService = new BaseUserServiceImpl(jwtUtil, fileUploadService, passwordEncoder);
        // 手动注入 MyBatis-Plus 的 baseMapper 字段
        ReflectionTestUtils.setField(userService, "baseMapper", baseMapper);

        testUser = BaseUser.builder()
                .id(1)
                .account("testuser")
                .password("$2a$10$dummyBcryptHashValueHere")
                .username("测试用户")
                .role(RoleEnum.STUDENT.getRole())
                .loginStatus(false)
                .email("test@example.com")
                .emailVerifyTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
    }

    // ── tokenAuth ──

    @Test
    void tokenAuth_shouldReturnUser_whenTokenValid() {
        when(jwtUtil.getClaims("valid-token")).thenReturn(mock(io.jsonwebtoken.Claims.class));
        when(jwtUtil.getUserId("valid-token")).thenReturn(1);
        when(baseMapper.selectById(1)).thenReturn(testUser);

        var result = userService.tokenAuth("valid-token");

        assertNotNull(result);
        assertEquals("testuser", result.getAccount());
        assertEquals("测试用户", result.getUsername());
    }

    @Test
    void tokenAuth_shouldThrow_whenClaimsNull() {
        when(jwtUtil.getClaims("bad-token")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.tokenAuth("bad-token"));
        assertEquals("token异常", ex.getMessage());
    }

    // ── updatePassword ──

    @Test
    void updatePassword_shouldSucceed_whenUserExists() {
        UserUpdatePasswordDTO dto = new UserUpdatePasswordDTO();
        dto.setPassword("newPassword");

        when(baseMapper.selectById(1)).thenReturn(testUser);
        when(passwordEncoder.encode("newPassword")).thenReturn("$2a$10$newHash");

        assertDoesNotThrow(() -> userService.updatePassword(1, dto));
        verify(baseMapper, times(1)).updateById((BaseUser) any());
    }

    @Test
    void updatePassword_shouldThrow_whenUserNotFound() {
        UserUpdatePasswordDTO dto = new UserUpdatePasswordDTO();
        dto.setPassword("newPassword");

        when(baseMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updatePassword(999, dto));
        assertEquals("用户信息查询异常", ex.getMessage());
    }
}
