package io.github.johneliud.identity_service.service;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.config.JwtTokenProvider;
import io.github.johneliud.identity_service.dto.RefreshTokenRequest;
import io.github.johneliud.identity_service.dto.RefreshTokenResponse;
import io.github.johneliud.identity_service.exception.AccountDeactivatedException;
import io.github.johneliud.identity_service.exception.InvalidRefreshTokenException;
import io.github.johneliud.identity_service.model.RefreshToken;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenHashUtil tokenHashUtil;

    public TokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            TokenHashUtil tokenHashUtil) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenHashUtil = tokenHashUtil;
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        String hashedToken = tokenHashUtil.hashToken(request.getRefreshToken());

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
        String newHashedRefreshToken = tokenHashUtil.hashToken(newRawRefreshToken);

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
        String hashedToken = tokenHashUtil.hashToken(request.getRefreshToken());

        refreshTokenRepository.findByTokenHashAndRevokedFalse(hashedToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("Refresh token revoked for user '{}'", token.getUser().getEmail());
                });
    }
}
