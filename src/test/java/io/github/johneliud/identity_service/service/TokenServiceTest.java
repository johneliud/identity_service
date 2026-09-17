package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenHashUtil tokenHashUtil;

    private TokenService tokenService;
    private Role travelerRole;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(refreshTokenRepository, jwtTokenProvider, tokenHashUtil);

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
    @DisplayName("Successful refresh rotates tokens and revokes old refresh token")
    void refresh_success_rotatesTokens() {
        User user = createActiveUser();
        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-old-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("old-raw-token")
                .build();

        when(tokenHashUtil.hashToken("old-raw-token")).thenReturn("hashed-old-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-old-token"))
                .thenReturn(Optional.of(existingToken));
        when(jwtTokenProvider.generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any(),
                any(), any(), any()))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh-token");
        when(tokenHashUtil.hashToken("new-raw-refresh-token")).thenReturn("new-hashed-refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenResponse response = tokenService.refresh(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-raw-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        assertThat(existingToken.getRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh with non-existent token throws InvalidRefreshTokenException")
    void refresh_nonExistentToken_throwsInvalidRefreshTokenException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("non-existent-token")
                .build();

        when(tokenHashUtil.hashToken("non-existent-token")).thenReturn("hashed-non-existent");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-non-existent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or revoked refresh token");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("Refresh with expired token throws InvalidRefreshTokenException")
    void refresh_expiredToken_throwsInvalidRefreshTokenException() {
        User user = createActiveUser();
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-expired-token")
                .user(user)
                .expiresAt(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("expired-raw-token")
                .build();

        when(tokenHashUtil.hashToken("expired-raw-token")).thenReturn("hashed-expired-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-expired-token"))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> tokenService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token has expired");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("Refresh with revoked token throws InvalidRefreshTokenException")
    void refresh_revokedToken_throwsInvalidRefreshTokenException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("revoked-raw-token")
                .build();

        when(tokenHashUtil.hashToken("revoked-raw-token")).thenReturn("hashed-revoked");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-revoked"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or revoked refresh token");
    }

    @Test
    @DisplayName("Refresh for deactivated account throws AccountDeactivatedException")
    void refresh_deactivatedAccount_throwsAccountDeactivatedException() {
        User user = createActiveUser();
        user.setStatus(UserStatus.DEACTIVATED);
        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("raw-token")
                .build();

        when(tokenHashUtil.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token"))
                .thenReturn(Optional.of(existingToken));

        assertThatThrownBy(() -> tokenService.refresh(request))
                .isInstanceOf(AccountDeactivatedException.class)
                .hasMessage("Account has been deactivated");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("Logout revokes the refresh token")
    void logout_success_revokesToken() {
        User user = createActiveUser();
        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("raw-token")
                .build();

        when(tokenHashUtil.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token"))
                .thenReturn(Optional.of(existingToken));

        tokenService.logout(request);

        assertThat(existingToken.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(existingToken);
    }

    @Test
    @DisplayName("Logout with non-existent token does not throw")
    void logout_nonExistentToken_doesNotThrow() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("non-existent-token")
                .build();

        when(tokenHashUtil.hashToken("non-existent-token")).thenReturn("hashed-non-existent");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-non-existent"))
                .thenReturn(Optional.empty());

        tokenService.logout(request);

        verify(refreshTokenRepository, never()).save(any());
    }
}
