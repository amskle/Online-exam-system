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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private static final String STATUS_AUTHENTICATED = "AUTHENTICATED";
    private static final String STATUS_EMAIL_REQUIRED = "EMAIL_REQUIRED";
    private static final String STATUS_VERIFICATION_REQUIRED = "EMAIL_VERIFICATION_REQUIRED";
    private static final String PURPOSE_LOGIN = "LOGIN";
    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final String CHALLENGE_PREFIX = "auth:challenge:";
    private static final String TRUSTED_PREFIX = "auth:trusted-device:";
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

    @Override
    public UserLoginResponseVO beginLogin(UserLoginDTO dto, Map<Integer, String> trustedDeviceTokens) {
        String account = dto.getAccount().trim();
        BaseUser user = findByAccount(account);
        if (user == null || !passwordMatches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误", 400);
        }
        if (Boolean.TRUE.equals(user.getLoginStatus())) {
            throw new BusinessException("账号已被停用，请联系管理员", 403);
        }

        upgradeLegacyPasswordIfNeeded(user, dto.getPassword());
        String trustedDeviceToken = trustedDeviceTokens.get(user.getId());
        if (StringUtils.hasText(trustedDeviceToken)
                && redisUtil.hasKey(trustedDeviceKey(user.getId(), trustedDeviceToken))) {
            return authenticated(user);
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
    public VerificationResult verify(EmailVerifyDTO dto) {
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
        return new VerificationResult(authenticated(user), deviceToken, user.getId());
    }

    private UserLoginResponseVO dispatchCode(String challengeId, String email, String purpose) {
        reserveEmailSend(email);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String key = challengeKey(challengeId);
        redisUtil.updateChallengeCode(key, email, hash(challengeId + ":" + code), codeTtl);
        try {
            emailUtil.sendVerificationCode(email, code, purpose);
        } catch (RuntimeException ex) {
            redisUtil.delete(key);
            throw new BusinessException("验证码邮件发送失败，请检查邮件配置后重试");
        }
        return challengeResponse(STATUS_VERIFICATION_REQUIRED, challengeId, email);
    }

    private void reserveEmailSend(String email) {
        String emailHash = hash(email);
        if (!redisUtil.setIfAbsent("auth:email:cooldown:" + emailHash, "1", sendCooldown)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试", 429);
        }
        String dailyKey = "auth:email:daily:" + LocalDate.now() + ":" + emailHash;
        if (redisUtil.increment(dailyKey, Duration.ofDays(2)) > dailyLimit) {
            throw new BusinessException("该邮箱今日发送次数已达上限", 429);
        }
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

    private UserLoginResponseVO authenticated(BaseUser user) {
        return UserLoginResponseVO.builder()
                .status(STATUS_AUTHENTICATED)
                .token(jwtUtil.generateToken(user.getId(), user.getRole()))
                .role(user.getRole())
                .roleName(RoleEnum.getByRole(user.getRole()).getDescription())
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
