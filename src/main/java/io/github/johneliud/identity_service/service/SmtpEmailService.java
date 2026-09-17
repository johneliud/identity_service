package io.github.johneliud.identity_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "identity.email.enabled", havingValue = "true")
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendBaseUrl;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${identity.email.from-address}") String fromAddress,
            @Value("${identity.email.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Verify your email address");
        message.setText(
                "Welcome to Safari Adventures!\n\n"
                + "Your verification code is:\n\n"
                + verificationToken + "\n\n"
                + "This code will expire in 1 hour.\n\n"
                + "If you did not create an account, you can safely ignore this email.");

        try {
            mailSender.send(message);
            log.info("Verification email sent to '{}'", to);
        } catch (Exception e) {
            log.error("Failed to send verification email to '{}': {}", to, e.getMessage());
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}
