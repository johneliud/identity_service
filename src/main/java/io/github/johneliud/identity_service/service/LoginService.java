package io.github.johneliud.identity_service.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.config.JwtTokenProvider;
import io.github.johneliud.identity_service.dto.LoginRequest;
import io.github.johneliud.identity_service.dto.LoginResponse;
import io.github.johneliud.identity_service.exception.AccountDeactivatedException;
import io.github.johneliud.identity_service.exception.AccountNotVerifiedException;
import io.github.johneliud.identity_service.exception.InvalidCredentialsException;
import io.github.johneliud.identity_service.model.RefreshToken;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RefreshTokenRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashUtil tokenHashUtil;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            TokenHashUtil tokenHashUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHashUtil = tokenHashUtil;
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
        String hashedRefreshToken = tokenHashUtil.hashToken(rawRefreshToken);

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
}
