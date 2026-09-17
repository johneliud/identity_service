package io.github.johneliud.identity_service.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.InvalidVerificationTokenException;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.model.VerificationToken;
import io.github.johneliud.identity_service.repository.UserRepository;
import io.github.johneliud.identity_service.repository.VerificationTokenRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailVerificationService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TokenHashUtil tokenHashUtil;
    private final EmailService emailService;
    private final long verificationTokenExpirationMs;

    public EmailVerificationService(
            VerificationTokenRepository verificationTokenRepository,
            UserRepository userRepository,
            OutboxEventPublisher outboxEventPublisher,
            TokenHashUtil tokenHashUtil,
            EmailService emailService,
            @Value("${identity.email-verification.token-expiration-ms}") long verificationTokenExpirationMs) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.userRepository = userRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.tokenHashUtil = tokenHashUtil;
        this.emailService = emailService;
        this.verificationTokenExpirationMs = verificationTokenExpirationMs;
    }

    @Transactional
    public String generateToken(User user) {
        String otpCode = String.format("%06d", new java.util.Random().nextInt(999999));
        String hashedToken = tokenHashUtil.hashToken(otpCode);

        VerificationToken verificationToken = VerificationToken.builder()
                .tokenHash(hashedToken)
                .user(user)
                .expiresAt(Instant.now().plusMillis(verificationTokenExpirationMs))
                .build();
        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user.getEmail(), otpCode);

        return otpCode;
    }

    @Transactional
    public void verify(String token) {
        String normalizedToken = token.trim();
        String hashedToken = tokenHashUtil.hashToken(normalizedToken);

        VerificationToken verificationToken = verificationTokenRepository.findByTokenHashAndUsedFalse(hashedToken)
                .orElseThrow(() -> {
                    log.warn("Email verification failed: invalid or already used token");
                    return new InvalidVerificationTokenException("Invalid or already used verification token");
                });

        if (verificationToken.isExpired()) {
            log.warn("Email verification failed: token expired");
            throw new InvalidVerificationTokenException("Verification token has expired");
        }

        User user = verificationToken.getUser();

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            log.warn("Email verification failed: account deactivated for user '{}'", user.getEmail());
            throw new InvalidVerificationTokenException("Account has been deactivated");
        }

        if (user.getEmailVerified()) {
            log.info("Email already verified for user '{}'", user.getEmail());
            verificationToken.setUsed(true);
            verificationTokenRepository.save(verificationToken);
            return;
        }

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        outboxEventPublisher.publishUserUpdated(user, "EMAIL_VERIFIED");

        log.info("Email verified successfully for user '{}'", user.getEmail());
    }
}
