package com.cinebook.notification.infra;

import com.cinebook.notification.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("Gui email toi {} — {} | {}", recipient, subject, body);
    }
}
