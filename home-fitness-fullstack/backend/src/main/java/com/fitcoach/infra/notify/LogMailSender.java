package com.fitcoach.infra.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 默认邮件发送实现 — 仅日志。生产可替换为 JavaMailSender 包装实现。
 */
@Slf4j
@Component
@Profile("!prod")
public class LogMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("[MAIL-MOCK] to={} subject={} body={}", mask(to), subject, text);
    }

    @Override
    public void sendCode(String to, String code, String purpose) {
        log.info("[MAIL-MOCK] to={} purpose={} code={}", mask(to), purpose, code);
    }

    @Override
    public String name() {
        return "log-mail";
    }

    private static String mask(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
