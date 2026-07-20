package com.example.onlineexamsystem.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮箱发送工具类
 */
@Component
@RequiredArgsConstructor
public class EmailUtil {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String sender;

    public void sendVerificationCode(String email, String code, String purpose) {
        String action = "REGISTER".equals(purpose) ? "注册" : "登录";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("在线考试系统邮箱验证码");
        message.setText("您正在进行" + action + "验证，验证码为：" + code
                + "\n\n验证码5分钟内有效，请勿向他人泄露。若非本人操作，请忽略此邮件。");
        mailSender.send(message);
    }
}
