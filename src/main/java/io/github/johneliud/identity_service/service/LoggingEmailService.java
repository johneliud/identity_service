package io.github.johneliud.identity_service.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "identity.email.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingEmailService implements EmailService {

    @Override
    public void sendVerificationEmail(String to, String verificationToken) {
        log.warn("EMAIL SERVICE DISABLED. Verification email NOT sent to '{}'. Token: '{}'",
                to, verificationToken);
    }
}
