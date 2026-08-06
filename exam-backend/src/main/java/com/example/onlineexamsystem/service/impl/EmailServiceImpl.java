package com.example.onlineexamsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.onlineexamsystem.common.exception.BusinessException;
import com.example.onlineexamsystem.pojo.dto.EmailSendDTO;
import com.example.onlineexamsystem.pojo.dto.EmailVerifyDTO;
import com.example.onlineexamsystem.pojo.dto.UserLoginDTO;
import com.example.onlineexamsystem.pojo.dto.UserRegisterDTO;
import com.example.onlineexamsystem.pojo.entity.BaseUser;
import com.example.onlineexamsystem.pojo.enums.AccountStatusEnum;
import com.example.onlineexamsystem.pojo.enums.RoleEnum;
import com.example.onlineexamsystem.pojo.vo.UserLoginResponseVO;
import com.example.onlineexamsystem.service.BaseUserService;
import com.example.onlineexamsystem.service.EmailService;
import com.example.onlineexamsystem.utils.EmailUtil;
import com.example.onlineexamsystem.utils.JwtUtil;
import com.example.onlineexamsystem.utils.RedisUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private static final String STATUS_AUTHENTICATED = "AUTHENTICATED";
    private static final String STATUS_EMAIL_REQUIRED = "EMAIL_REQUIRED";
    private static final String STATUS_VERIFICATION_REQUIRED = "EMAIL_VERIFICATION_REQUIRED";
    private static final String PURPOSE_LOGIN = "LOGIN";
    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final String CHALLENGE_PREFIX = "auth:challenge:";
    private static final String TRUSTED_PREFIX = "auth:trusted-device:";
    private static final String LOGIN_VERSION_PREFIX = "user:login_version:";
    private static final String AUTH_COOKIE_NAME = "exam_token";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final BaseUserService baseUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final EmailUtil emailUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.email-code-ttl:5m}")
    private Duration codeTtl;

    @Value("${auth.email-send-cooldown:60s}")
    private Duration sendCooldown;

    @Value("${auth.email-daily-limit:10}")
    private int dailyLimit;

    @Value("${auth.trusted-device-ttl:7d}")
    private Duration trustedDeviceTtl;

    @Value("${auth.trusted-device-secure-cookie:false}")
    private boolean secureCookie;

    @Override
    public UserLoginResponseVO beginLogin(UserLoginDTO dto, Map<Integer, String> trustedDeviceTokens,
                                          HttpServletResponse response) {
        String account = dto.getAccount().trim();
        String failKey = LOGIN_FAIL_PREFIX + account;

        // ① 检查是否已被锁定
        String failCountStr = redisUtil.get(failKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_ATTEMPTS) {
            long remainingSeconds = redisUtil.getExpireSeconds(failKey);
            throw new BusinessException(
                    "账号已被锁定，请 " + (remainingSeconds / 60 + 1) + " 分钟后重试");
        }

        BaseUser user = findByAccount(account);
        if (user == null || !passwordMatches(dto.getPassword(), user.getPassword())) {
            recordLoginFailure(failKey);  // 记录失败次数
            throw new BusinessException("账号或密码错误", 400);
        }
        if (Boolean.TRUE.equals(user.getLoginStatus())) {
            throw new BusinessException("账号已被停用，请联系管理员", 403);
        }

        upgradeLegacyPasswordIfNeeded(user, dto.getPassword());
        String trustedDeviceToken = trustedDeviceTokens.get(user.getId());
        if (StringUtils.hasText(trustedDeviceToken)
                && redisUtil.hasKey(trustedDeviceKey(user.getId(), trustedDeviceToken))) {
            return authenticated(user, response);
        }
        // 回落：Redis 重启后信任设备 token 丢失，但 email_verify_time 存于 MySQL 不会丢
        if (user.getEmailVerifyTime() != null
                && Duration.between(user.getEmailVerifyTime(), LocalDateTime.now()).compareTo(trustedDeviceTtl) < 0) {
            return authenticated(user, response);
        }

        String challengeId = UUID.randomUUID().toString();
        Map<String, String> challenge = new HashMap<>();
        challenge.put("purpose", PURPOSE_LOGIN);
        challenge.put("userId", String.valueOf(user.getId()));
        challenge.put("attempts", "0");
        challenge.put("state", StringUtils.hasText(user.getEmail()) ? "CREATED" : "AWAIT_EMAIL");
        if (StringUtils.hasText(user.getEmail())) {
            challenge.put("email", normalizeEmail(user.getEmail()));
        }
        redisUtil.putHash(challengeKey(challengeId), challenge, codeTtl);

        if (!StringUtils.hasText(user.getEmail())) {
            return challengeResponse(STATUS_EMAIL_REQUIRED, challengeId, null);
        }
        return dispatchCode(challengeId, normalizeEmail(user.getEmail()), PURPOSE_LOGIN);
    }

    @Override
    public UserLoginResponseVO beginRegister(UserRegisterDTO dto) {
        String account = dto.getAccount().trim();
        String email = normalizeEmail(dto.getEmail());
        ensureAccountAvailable(account);
        ensureEmailAvailable(email, null);

        String challengeId = UUID.randomUUID().toString();
        Map<String, String> challenge = new HashMap<>();
        challenge.put("purpose", PURPOSE_REGISTER);
        challenge.put("account", account);
        challenge.put("password", passwordEncoder.encode(dto.getPassword()));
        challenge.put("username", dto.getUsername().trim());
        challenge.put("role", String.valueOf(dto.getRole()));
        challenge.put("email", email);
        challenge.put("attempts", "0");
        challenge.put("state", "CREATED");
        redisUtil.putHash(challengeKey(challengeId), challenge, codeTtl);
        return dispatchCode(challengeId, email, PURPOSE_REGISTER);
    }

    @Override
    public UserLoginResponseVO sendCode(EmailSendDTO dto) {
        String key = challengeKey(dto.getChallengeId());
        Map<String, String> challenge = redisUtil.getHash(key);
        if (challenge.isEmpty()) {
            throw new BusinessException("验证请求已过期，请重新操作", 400);
        }

        String purpose = challenge.get("purpose");
        String email = challenge.get("email");
        if (!StringUtils.hasText(email)) {
            if (!PURPOSE_LOGIN.equals(purpose) || !StringUtils.hasText(dto.getEmail())) {
                throw new BusinessException("请输入接收验证码的邮箱", 400);
            }
            email = normalizeEmail(dto.getEmail());
            Integer userId = Integer.valueOf(challenge.get("userId"));
            ensureEmailAvailable(email, userId);
        }
        return dispatchCode(dto.getChallengeId(), email, purpose);
    }

    @Override
    @Transactional
    public VerificationResult verify(EmailVerifyDTO dto, HttpServletResponse response) {
        String key = challengeKey(dto.getChallengeId());
        Map<String, String> challenge = redisUtil.getHash(key);
        if (challenge.isEmpty()) {
            throw new BusinessException("验证码已过期，请重新操作", 400);
        }
        if (!"CODE_SENT".equals(challenge.get("state"))) {
            throw new BusinessException("请先发送验证码", 400);
        }

        long verifyResult = redisUtil.verifyAndDeleteCode(
                key,
                hash(dto.getChallengeId() + ":" + dto.getCode()),
                MAX_VERIFY_ATTEMPTS
        );
        if (verifyResult == 0) {
            throw new BusinessException("验证码错误", 400);
        }
        if (verifyResult == -1) {
            throw new BusinessException("验证码错误次数过多，请重新操作", 400);
        }
        if (verifyResult != 1) {
            throw new BusinessException("验证码已过期，请重新操作", 400);
        }

        BaseUser user = PURPOSE_REGISTER.equals(challenge.get("purpose"))
                ? createRegisteredUser(challenge)
                : finishLogin(challenge);
        String deviceToken = null;
        if (dto.isTrustDevice()) {
            deviceToken = createTrustedDevice(user.getId());
        }
        return new VerificationResult(authenticated(user, response), deviceToken, user.getId());
    }

    private UserLoginResponseVO dispatchCode(String challengeId, String email, String purpose) {
        reserveEmailSend(email);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String key = challengeKey(challengeId);
        redisUtil.updateChallengeCode(key, email, hash(challengeId + ":" + code), codeTtl);
        try {
            emailUtil.sendVerificationCode(email, code, purpose);
        } catch (RuntimeException ex) {
            log.error("验证码邮件发送失败，email={}, purpose={}", email, purpose, ex);
            redisUtil.delete(key);
            redisUtil.delete(emailCooldownKey(email));
            redisUtil.decrementIfExists(emailDailyKey(email));
            throw new BusinessException("验证码邮件发送失败，请检查邮件配置后重试");
        }
        return challengeResponse(STATUS_VERIFICATION_REQUIRED, challengeId, email);
    }

    private void reserveEmailSend(String email) {
        if (!redisUtil.setIfAbsent(emailCooldownKey(email), "1", sendCooldown)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试", 429);
        }
        if (redisUtil.increment(emailDailyKey(email), Duration.ofDays(2)) > dailyLimit) {
            redisUtil.delete(emailCooldownKey(email));
            throw new BusinessException("该邮箱今日发送次数已达上限", 429);
        }
    }

    private String emailCooldownKey(String email) {
        return "auth:email:cooldown:" + hash(email);
    }

    private String emailDailyKey(String email) {
        return "auth:email:daily:" + LocalDate.now() + ":" + hash(email);
    }

    private BaseUser createRegisteredUser(Map<String, String> challenge) {
        ensureAccountAvailable(challenge.get("account"));
        ensureEmailAvailable(challenge.get("email"), null);
        BaseUser user = BaseUser.builder()
                .account(challenge.get("account"))
                .password(challenge.get("password"))
                .username(challenge.get("username"))
                .role(Integer.valueOf(challenge.get("role")))
                .email(challenge.get("email"))
                .emailVerifyTime(LocalDateTime.now())
                .loginStatus(AccountStatusEnum.NORMAL.getStatusCode())
                .createTime(LocalDateTime.now())
                .build();
        baseUserService.save(user);
        return user;
    }

    private BaseUser finishLogin(Map<String, String> challenge) {
        BaseUser user = baseUserService.getById(Integer.valueOf(challenge.get("userId")));
        if (user == null || Boolean.TRUE.equals(user.getLoginStatus())) {
            throw new BusinessException("账号状态异常，请重新登录", 403);
        }
        String verifiedEmail = challenge.get("email");
        if (!StringUtils.hasText(user.getEmail())) {
            ensureEmailAvailable(verifiedEmail, user.getId());
            user.setEmail(verifiedEmail);
        } else if (!Objects.equals(normalizeEmail(user.getEmail()), verifiedEmail)) {
            throw new BusinessException("邮箱信息已变更，请重新登录", 400);
        }
        user.setEmailVerifyTime(LocalDateTime.now());
        baseUserService.updateById(user);
        return user;
    }

    private UserLoginResponseVO authenticated(BaseUser user, HttpServletResponse response) {
        // 登录成功 → 清除失败计数
        redisUtil.delete(LOGIN_FAIL_PREFIX + user.getAccount());

        // 生成新登录版本号，使旧 token 失效（顶号）
        String loginVersion = UUID.randomUUID().toString();
        redisUtil.put(LOGIN_VERSION_PREFIX + user.getId(), loginVersion, trustedDeviceTtl);
        String token = jwtUtil.generateToken(user.getId(), user.getRole(), loginVersion);

        // 设置 HttpOnly Cookie，防止 XSS 窃取 Token
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(trustedDeviceTtl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return UserLoginResponseVO.builder()
                .status(STATUS_AUTHENTICATED)
                .userId(user.getId())
                .roleName(RoleEnum.getByRole(user.getRole()).getDescription())
                .token(token)
                .build();
    }

    private UserLoginResponseVO challengeResponse(String status, String challengeId, String email) {
        return UserLoginResponseVO.builder()
                .status(status)
                .challengeId(challengeId)
                .maskedEmail(StringUtils.hasText(email) ? maskEmail(email) : null)
                .expiresIn(redisUtil.getExpireSeconds(challengeKey(challengeId)))
                .build();
    }

    private BaseUser findByAccount(String account) {
        return baseUserService.getOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getAccount, account));
    }

    private void ensureAccountAvailable(String account) {
        if (findByAccount(account) != null) {
            throw new BusinessException("账号不可用", 400);
        }
    }

    private void ensureEmailAvailable(String email, Integer allowedUserId) {
        BaseUser existing = baseUserService.getOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getEmail, email));
        if (existing != null && !Objects.equals(existing.getId(), allowedUserId)) {
            throw new BusinessException("邮箱已被其他账号使用", 400);
        }
    }

    private void recordLoginFailure(String failKey) {
        long count = redisUtil.recordLoginFailure(failKey, LOGIN_LOCK_DURATION, MAX_LOGIN_ATTEMPTS);
        if (count >= MAX_LOGIN_ATTEMPTS) {
            throw new BusinessException("密码错误次数过多，账号已被锁定15分钟");
        }
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        return storedPassword.startsWith("$2")
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : Objects.equals(rawPassword, storedPassword);
    }

    private void upgradeLegacyPasswordIfNeeded(BaseUser user, String rawPassword) {
        if (!user.getPassword().startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            baseUserService.updateById(user);
        }
    }

    private String createTrustedDevice(Integer userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisUtil.setIfAbsent(trustedDeviceKey(userId, token), "1", trustedDeviceTtl);
        return token;
    }

    private String trustedDeviceKey(Integer userId, String rawToken) {
        return TRUSTED_PREFIX + userId + ":" + hash(rawToken);
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_PREFIX + challengeId;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "****" + email.substring(at);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }
}
