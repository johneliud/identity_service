package io.github.johneliud.identity_service.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.config.JwtTokenProvider;
import io.github.johneliud.identity_service.dto.ChangePasswordRequest;
import io.github.johneliud.identity_service.dto.ForgotPasswordRequest;
import io.github.johneliud.identity_service.dto.ResetPasswordRequest;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.AccountDeactivatedException;
import io.github.johneliud.identity_service.exception.InvalidCredentialsException;
import io.github.johneliud.identity_service.exception.InvalidResetTokenException;
import io.github.johneliud.identity_service.model.ResetToken;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.ResetTokenRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PasswordService {

    private static final long RESET_TOKEN_EXPIRATION_MS = 3600000;
    private static final java.util.Random RANDOM = new java.util.Random();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ResetTokenRepository resetTokenRepository;
    private final TokenHashUtil tokenHashUtil;
    private final EmailService emailService;

    public PasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OutboxEventPublisher outboxEventPublisher,
            ResetTokenRepository resetTokenRepository,
            TokenHashUtil tokenHashUtil,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventPublisher = outboxEventPublisher;
        this.resetTokenRepository = resetTokenRepository;
        this.tokenHashUtil = tokenHashUtil;
        this.emailService = emailService;
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String userId) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new AccountDeactivatedException("Account has been deactivated");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("Change password rejected: invalid current password for user '{}'", user.getEmail());
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            log.warn("Change password rejected: new password same as current for user '{}'", user.getEmail());
            throw new InvalidCredentialsException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        outboxEventPublisher.publishUserUpdated(user, "PASSWORD_CHANGED");

        log.info("Password changed successfully for user '{}'", user.getEmail());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            String otpCode = String.format("%06d", RANDOM.nextInt(999999));
            String hashedToken = tokenHashUtil.hashToken(otpCode);

            ResetToken resetToken = ResetToken.builder()
                    .tokenHash(hashedToken)
                    .user(user)
                    .expiresAt(Instant.now().plusMillis(RESET_TOKEN_EXPIRATION_MS))
                    .build();
            resetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), otpCode);
        });

        log.info("Password reset requested for email '{}'", normalizedEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hashedToken = tokenHashUtil.hashToken(request.getCode());

        ResetToken resetToken = resetTokenRepository.findByTokenHashAndUsedFalse(hashedToken)
                .orElseThrow(() -> {
                    log.warn("Password reset failed: invalid or already used token");
                    return new InvalidResetTokenException("Invalid or already used reset token");
                });

        if (resetToken.isExpired()) {
            log.warn("Password reset failed: token expired");
            throw new InvalidResetTokenException("Reset token has expired");
        }

        User user = resetToken.getUser();

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new AccountDeactivatedException("Account has been deactivated");
        }

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        outboxEventPublisher.publishUserUpdated(user, "PASSWORD_RESET");

        log.info("Password reset successfully for user '{}'", user.getEmail());
    }
}
