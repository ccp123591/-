package com.fitcoach.infra.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 生产环境 SMTP 邮件发送实现。prod 启动时必须配置 spring.mail.host，
 * 否则 JavaMailSender 不会创建，应用会启动失败，避免安全告警静默退回日志 mock。
 */
@Component
@Profile("prod")
public class SmtpMailSender implements MailSender {

    private final JavaMailSender delegate;
    private final String from;

    public SmtpMailSender(JavaMailSender delegate, @Value("${spring.mail.from}") String from) {
        this.delegate = delegate;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        delegate.send(message);
    }

    @Override
    public void sendCode(String to, String code, String purpose) {
        send(to, "FitCoach 验证码", "您的验证码是：" + code + "。用途：" + purpose + "。请勿泄露给他人。");
    }

    @Override
    public String name() {
        return "smtp-mail";
    }
}
