package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.InvalidVerificationTokenException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.model.VerificationToken;
import io.github.johneliud.identity_service.repository.UserRepository;
import io.github.johneliud.identity_service.repository.VerificationTokenRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private TokenHashUtil tokenHashUtil;

    @Mock
    private EmailService emailService;

    private EmailVerificationService emailVerificationService;
    private Role travelerRole;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(
                verificationTokenRepository, userRepository, outboxEventPublisher,
                tokenHashUtil, emailService, 3600000L
        );

        travelerRole = Role.builder()
                .id(1L)
                .name("TRAVELER")
                .description("Corporate traveler")
                .build();
    }

    private User createPendingUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("$2a$12$hashedPasswordExample")
                .firstName("John")
                .lastName("Doe")
                .status(UserStatus.PENDING)
                .emailVerified(false)
                .roles(Set.of(travelerRole))
                .build();
    }

    @Test
    @DisplayName("generateToken creates verification token and returns 6-digit OTP")
    void generateToken_success() {
        User user = createPendingUser();

        when(tokenHashUtil.hashToken(anyString())).thenReturn("hashed-token");
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String otpCode = emailVerificationService.generateToken(user);

        assertThat(otpCode).isNotNull();
        assertThat(otpCode).hasSize(6);
        assertThat(otpCode).matches("\\d{6}");
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail("user@example.com", otpCode);
    }

    @Test
    @DisplayName("verify succeeds with valid token and activates user")
    void verify_success() {
        User user = createPendingUser();
        VerificationToken verificationToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-verify-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("raw-verify-token")).thenReturn("hashed-verify-token");
        when(verificationTokenRepository.findByTokenHashAndUsedFalse("hashed-verify-token"))
                .thenReturn(Optional.of(verificationToken));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        emailVerificationService.verify("raw-verify-token");

        assertThat(verificationToken.getUsed()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(outboxEventPublisher).publishUserUpdated(user, "EMAIL_VERIFIED");
    }

    @Test
    @DisplayName("verify throws InvalidVerificationTokenException for invalid token")
    void verify_invalidToken_throwsInvalidVerificationToken() {
        when(tokenHashUtil.hashToken("invalid-token")).thenReturn("hashed-invalid");
        when(verificationTokenRepository.findByTokenHashAndUsedFalse("hashed-invalid"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verify("invalid-token"))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Invalid or already used verification token");
    }

    @Test
    @DisplayName("verify throws InvalidVerificationTokenException for expired token")
    void verify_expiredToken_throwsInvalidVerificationToken() {
        User user = createPendingUser();
        VerificationToken expiredToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-expired-token")
                .user(user)
                .expiresAt(Instant.now().minusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("expired-token")).thenReturn("hashed-expired-token");
        when(verificationTokenRepository.findByTokenHashAndUsedFalse("hashed-expired-token"))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> emailVerificationService.verify("expired-token"))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Verification token has expired");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("verify throws InvalidVerificationTokenException for deactivated account")
    void verify_deactivatedAccount_throwsInvalidVerificationToken() {
        User user = createPendingUser();
        user.setStatus(UserStatus.DEACTIVATED);
        VerificationToken verificationToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("raw-token")).thenReturn("hashed-token");
        when(verificationTokenRepository.findByTokenHashAndUsedFalse("hashed-token"))
                .thenReturn(Optional.of(verificationToken));

        assertThatThrownBy(() -> emailVerificationService.verify("raw-token"))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessage("Account has been deactivated");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("verify is idempotent for already verified user")
    void verify_alreadyVerified_idempotent() {
        User user = createPendingUser();
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        VerificationToken verificationToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("raw-token")).thenReturn("hashed-token");
        when(verificationTokenRepository.findByTokenHashAndUsedFalse("hashed-token"))
                .thenReturn(Optional.of(verificationToken));
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        emailVerificationService.verify("raw-token");

        assertThat(verificationToken.getUsed()).isTrue();
        verify(userRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishUserUpdated(any(), anyString());
    }
}
