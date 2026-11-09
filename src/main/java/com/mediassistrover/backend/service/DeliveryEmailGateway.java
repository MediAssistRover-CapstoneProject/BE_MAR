package com.mediassistrover.backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryEmailGateway {
    /*
     * MailSenderAutoConfiguration only creates this bean when spring.mail.host is
     * visible to the running process. Keep it optional so a missing/misplaced EC2
     * environment file cannot prevent the whole backend from starting.
     */
    private final ObjectProvider<JavaMailSender> senders;

    public boolean available() {
        return senders.getIfAvailable() != null;
    }

    public void send(String from, String to, String subject, String text) throws Exception {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            throw new MailSendException("SMTP is not configured: spring.mail.host is unavailable");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, false);
        sender.send(message);
    }
}
