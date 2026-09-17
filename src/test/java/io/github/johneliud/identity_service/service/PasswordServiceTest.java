package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.johneliud.identity_service.dto.ChangePasswordRequest;
import io.github.johneliud.identity_service.dto.ForgotPasswordRequest;
import io.github.johneliud.identity_service.dto.ResetPasswordRequest;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.AccountDeactivatedException;
import io.github.johneliud.identity_service.exception.InvalidCredentialsException;
import io.github.johneliud.identity_service.exception.InvalidResetTokenException;
import io.github.johneliud.identity_service.model.ResetToken;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.ResetTokenRepository;
import io.github.johneliud.identity_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private ResetTokenRepository resetTokenRepository;

    @Mock
    private TokenHashUtil tokenHashUtil;

    @Mock
    private EmailService emailService;

    private PasswordService passwordService;
    private Role travelerRole;
    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";
    private static final String NEW_PASSWORD = UUID.randomUUID() + "Bb2!";

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(
                userRepository, passwordEncoder, outboxEventPublisher,
                resetTokenRepository, tokenHashUtil, emailService
        );

        travelerRole = Role.builder()
                .id(1L)
                .name("TRAVELER")
                .description("Corporate traveler")
                .build();
    }

    private User createActiveUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("$2a$12$hashedPasswordExample")
                .firstName("John")
                .lastName("Doe")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(Set.of(travelerRole))
                .build();
    }

    @Test
    @DisplayName("Change password succeeds with correct current password")
    void changePassword_success() {
        User user = createActiveUser();
        String userId = user.getId().toString();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("$2a$12$newHashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword(USER_PASSWORD)
                .newPassword(NEW_PASSWORD)
                .build();

        passwordService.changePassword(request, userId);

        verify(userRepository).save(any(User.class));
        verify(outboxEventPublisher).publishUserUpdated(any(User.class), anyString());
    }

    @Test
    @DisplayName("Change password fails with wrong current password")
    void changePassword_wrongCurrentPassword_throwsInvalidCredentials() {
        User user = createActiveUser();
        String userId = user.getId().toString();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).thenReturn(false);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword(USER_PASSWORD)
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> passwordService.changePassword(request, userId))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Change password fails when new password same as current")
    void changePassword_samePassword_throwsInvalidCredentials() {
        User user = createActiveUser();
        String userId = user.getId().toString();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).thenReturn(true);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword(USER_PASSWORD)
                .newPassword(USER_PASSWORD)
                .build();

        assertThatThrownBy(() -> passwordService.changePassword(request, userId))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("New password must be different from current password");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Change password fails for deactivated account")
    void changePassword_deactivatedAccount_throwsAccountDeactivated() {
        User user = createActiveUser();
        user.setStatus(UserStatus.DEACTIVATED);
        String userId = user.getId().toString();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword(USER_PASSWORD)
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> passwordService.changePassword(request, userId))
                .isInstanceOf(AccountDeactivatedException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Forgot password generates OTP code and sends email for existing user")
    void forgotPassword_success() {
        User user = createActiveUser();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(tokenHashUtil.hashToken(anyString())).thenReturn("hashed-otp");
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("user@example.com")
                .build();

        passwordService.forgotPassword(request);

        verify(resetTokenRepository).save(any());
        verify(emailService).sendPasswordResetEmail(eq("user@example.com"), anyString());
    }

    @Test
    @DisplayName("Forgot password does not fail for non-existent email (prevents enumeration)")
    void forgotPassword_nonExistentEmail_doesNotThrow() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("nonexistent@example.com")
                .build();

        passwordService.forgotPassword(request);

        verify(resetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Reset password succeeds with valid code")
    void resetPassword_success() {
        User user = createActiveUser();
        ResetToken resetToken = ResetToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-otp")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("123456")).thenReturn("hashed-otp");
        when(resetTokenRepository.findByTokenHashAndUsedFalse("hashed-otp"))
                .thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("$2a$12$newHashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(resetTokenRepository.save(any(ResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("123456")
                .newPassword(NEW_PASSWORD)
                .build();

        passwordService.resetPassword(request);

        assertThat(resetToken.getUsed()).isTrue();
        verify(userRepository).save(any(User.class));
        verify(outboxEventPublisher).publishUserUpdated(any(User.class), anyString());
    }

    @Test
    @DisplayName("Reset password fails with invalid code")
    void resetPassword_invalidCode_throwsInvalidResetToken() {
        when(tokenHashUtil.hashToken("000000")).thenReturn("hashed-invalid");
        when(resetTokenRepository.findByTokenHashAndUsedFalse("hashed-invalid"))
                .thenReturn(Optional.empty());

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("000000")
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> passwordService.resetPassword(request))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or already used reset token");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password fails with expired code")
    void resetPassword_expiredCode_throwsInvalidResetToken() {
        User user = createActiveUser();
        ResetToken expiredToken = ResetToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-expired")
                .user(user)
                .expiresAt(Instant.now().minusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("111111")).thenReturn("hashed-expired");
        when(resetTokenRepository.findByTokenHashAndUsedFalse("hashed-expired"))
                .thenReturn(Optional.of(expiredToken));

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("111111")
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> passwordService.resetPassword(request))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Reset token has expired");
    }

    @Test
    @DisplayName("Reset password fails for deactivated account")
    void resetPassword_deactivatedAccount_throwsAccountDeactivated() {
        User user = createActiveUser();
        user.setStatus(UserStatus.DEACTIVATED);
        ResetToken resetToken = ResetToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .used(false)
                .build();

        when(tokenHashUtil.hashToken("222222")).thenReturn("hashed-token");
        when(resetTokenRepository.findByTokenHashAndUsedFalse("hashed-token"))
                .thenReturn(Optional.of(resetToken));

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("222222")
                .newPassword(NEW_PASSWORD)
                .build();

        assertThatThrownBy(() -> passwordService.resetPassword(request))
                .isInstanceOf(AccountDeactivatedException.class);
    }
}
