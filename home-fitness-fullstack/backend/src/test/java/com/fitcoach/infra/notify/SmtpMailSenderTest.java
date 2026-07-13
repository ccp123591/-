package com.fitcoach.infra.notify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpMailSenderTest {

    @Test
    void send_delegates_complete_message_to_java_mail_sender() {
        JavaMailSender delegate = mock(JavaMailSender.class);
        SmtpMailSender sender = new SmtpMailSender(delegate, "alerts@fitcoach.example.com");

        sender.send("family@example.com", "安全告警", "检测到疑似跌倒");

        var captor = forClass(SimpleMailMessage.class);
        verify(delegate).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("alerts@fitcoach.example.com");
        assertThat(captor.getValue().getTo()).containsExactly("family@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("安全告警");
        assertThat(captor.getValue().getText()).isEqualTo("检测到疑似跌倒");
        assertThat(sender.name()).isEqualTo("smtp-mail");
    }
}
