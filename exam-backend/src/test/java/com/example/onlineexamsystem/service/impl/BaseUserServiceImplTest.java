package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.mapper.BaseUserMapper;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.dto.UserUpdatePasswordDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.enums.RoleEnum;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import com.example.onlineexamsystem.utils.JwtUtil;
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
 * BaseUserServiceImpl 单元测试 — 覆盖登录、注册、token验证、修改密码
 */
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

    // ── login ──

    @Test
    void login_shouldReturnToken_whenCredentialsValid() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount("testuser");
        dto.setPassword("correctPassword");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);
        when(passwordEncoder.matches("correctPassword", testUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(1, RoleEnum.STUDENT.getRole())).thenReturn("mocked-jwt-token");

        UserLoginResponseVO result = userService.login(dto);

        assertNotNull(result);
        assertEquals("AUTHENTICATED", result.getStatus());
        assertEquals("mocked-jwt-token", result.getToken());
        assertEquals(RoleEnum.STUDENT.getRole(), result.getRole());
        assertNotNull(result.getRoleName());
    }

    @Test
    void login_shouldThrow_whenAccountNotFound() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount("nonexistent");
        dto.setPassword("any");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));
        assertEquals("账号不存在", ex.getMessage());
    }

    @Test
    void login_shouldThrow_whenPasswordWrong() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount("testuser");
        dto.setPassword("wrongPassword");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);
        when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));
        assertEquals("密码错误", ex.getMessage());
    }

    @Test
    void login_shouldThrow_whenAccountDisabled() {
        testUser.setLoginStatus(true);
        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount("testuser");
        dto.setPassword("correctPassword");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);
        when(passwordEncoder.matches("correctPassword", testUser.getPassword())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));
        assertEquals("账号已被停用，请联系管理员", ex.getMessage());
    }

    @Test
    void login_shouldSupportPlainTextPassword_forLegacyUsers() {
        testUser.setPassword("plaintext_password");
        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount("testuser");
        dto.setPassword("plaintext_password");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);
        when(jwtUtil.generateToken(1, RoleEnum.STUDENT.getRole())).thenReturn("mocked-jwt");

        UserLoginResponseVO result = userService.login(dto);

        assertNotNull(result);
        assertEquals("AUTHENTICATED", result.getStatus());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ── register ──

    @Test
    void register_shouldSaveUser_whenAccountAvailable() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setAccount("newuser");
        dto.setPassword("password123");
        dto.setUsername("新用户");
        dto.setRole(RoleEnum.STUDENT.getRole());
        dto.setEmail("new@example.com");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");

        assertDoesNotThrow(() -> userService.register(dto));
        verify(baseMapper, times(1)).insert((BaseUser) any());
    }

    @Test
    void register_shouldThrow_whenAccountExists() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setAccount("testuser");
        dto.setPassword("password123");

        when(baseMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(testUser);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));
        assertEquals("账号不可用", ex.getMessage());
        verify(baseMapper, never()).insert((BaseUser) any());
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
