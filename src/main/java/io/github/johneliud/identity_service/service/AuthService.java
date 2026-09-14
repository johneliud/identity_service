package io.github.johneliud.identity_service.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.config.JwtTokenProvider;
import io.github.johneliud.identity_service.dto.ChangePasswordRequest;
import io.github.johneliud.identity_service.dto.ForgotPasswordRequest;
import io.github.johneliud.identity_service.dto.LoginRequest;
import io.github.johneliud.identity_service.dto.LoginResponse;
import io.github.johneliud.identity_service.dto.RefreshTokenRequest;
import io.github.johneliud.identity_service.dto.RefreshTokenResponse;
import io.github.johneliud.identity_service.dto.RegisterRequest;
import io.github.johneliud.identity_service.dto.ResetPasswordRequest;
import io.github.johneliud.identity_service.dto.UserResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.event.UserRegisteredEvent;
import io.github.johneliud.identity_service.exception.AccountDeactivatedException;
import io.github.johneliud.identity_service.exception.AccountNotVerifiedException;
import io.github.johneliud.identity_service.exception.InvalidCredentialsException;
import io.github.johneliud.identity_service.exception.InvalidRefreshTokenException;
import io.github.johneliud.identity_service.exception.InvalidResetTokenException;
import io.github.johneliud.identity_service.exception.RoleNotFoundException;
import io.github.johneliud.identity_service.exception.UserAlreadyExistsException;
import io.github.johneliud.identity_service.model.RefreshToken;
import io.github.johneliud.identity_service.model.ResetToken;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RefreshTokenRepository;
import io.github.johneliud.identity_service.repository.ResetTokenRepository;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthService {

    public static final String DEFAULT_ROLE = "TRAVELER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final boolean requireEmailVerification;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            OutboxEventPublisher outboxEventPublisher,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            ResetTokenRepository resetTokenRepository,
            @Value("${identity.registration.require-email-verification:true}") boolean requireEmailVerification) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventPublisher = outboxEventPublisher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected: email already in use");
            throw new UserAlreadyExistsException("User with email '" + normalizedEmail + "' already exists");
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> {
                    log.error("Default role '{}' not found in database", DEFAULT_ROLE);
                    return new RoleNotFoundException("Default role '" + DEFAULT_ROLE + "' not found");
                });

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        UserStatus initialStatus = requireEmailVerification ? UserStatus.PENDING : UserStatus.ACTIVE;
        boolean emailVerified = !requireEmailVerification;

        String trimmedLastName = (request.getLastName() != null && !request.getLastName().isBlank())
                ? request.getLastName().trim()
                : null;

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(hashedPassword)
                .firstName(request.getFirstName().trim())
                .lastName(trimmedLastName)
                .status(initialStatus)
                .emailVerified(emailVerified)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully registered new user: id='{}', status='{}'",
                savedUser.getId(), savedUser.getStatus());

        Set<String> roleNames = savedUser.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        UserRegisteredEvent event = new UserRegisteredEvent(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getStatus().name(),
                roleNames,
                Instant.now()
        );

        outboxEventPublisher.publishUserRegistered(event);

        return UserResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .status(savedUser.getStatus())
                .emailVerified(savedUser.getEmailVerified())
                .roles(roleNames)
                .createdAt(savedUser.getCreatedAt())
                .updatedAt(savedUser.getUpdatedAt())
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found");
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            log.warn("Login rejected: account deactivated for user '{}'", normalizedEmail);
            throw new AccountDeactivatedException("Account has been deactivated");
        }

        if (user.getStatus() == UserStatus.PENDING && !user.getEmailVerified()) {
            log.warn("Login rejected: email not verified for user '{}'", normalizedEmail);
            throw new AccountNotVerifiedException("Email address has not been verified");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user '{}'", normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId().toString(), roleNames);

        String rawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String hashedRefreshToken = hashToken(rawRefreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .tokenHash(hashedRefreshToken)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        log.info("Successful login for user");

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());

        RefreshToken existingToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(hashedToken)
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token not found or revoked");
                    return new InvalidRefreshTokenException("Invalid or revoked refresh token");
                });

        if (existingToken.isExpired()) {
            log.warn("Refresh failed: token expired for user '{}'", existingToken.getUser().getEmail());
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = existingToken.getUser();

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            log.warn("Refresh rejected: account deactivated for user '{}'", user.getEmail());
            throw new AccountDeactivatedException("Account has been deactivated");
        }

        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId().toString(), roleNames);

        String newRawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String newHashedRefreshToken = hashToken(newRawRefreshToken);

        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .tokenHash(newHashedRefreshToken)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(newRefreshTokenEntity);

        log.info("Successful token refresh for user '{}'", user.getEmail());

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());

        refreshTokenRepository.findByTokenHashAndRevokedFalse(hashedToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("Refresh token revoked for user '{}'", token.getUser().getEmail());
                });
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
            String rawToken = jwtTokenProvider.generateRefreshToken();
            String hashedToken = hashToken(rawToken);

            ResetToken resetToken = ResetToken.builder()
                    .tokenHash(hashedToken)
                    .user(user)
                    .expiresAt(Instant.now().plusMillis(
                            jwtTokenProvider.getRefreshTokenExpirationMs() > 3600000
                                    ? 3600000
                                    : jwtTokenProvider.getRefreshTokenExpirationMs()))
                    .build();
            resetTokenRepository.save(resetToken);

            log.info("Password reset token generated for user '{}'. Token: '{}' would be sent via email",
                    user.getEmail(), rawToken);
        });

        log.info("Password reset requested for email '{}'", normalizedEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hashedToken = hashToken(request.getToken());

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

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
