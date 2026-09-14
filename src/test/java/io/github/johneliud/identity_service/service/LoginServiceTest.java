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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenHashUtil tokenHashUtil;

    private LoginService loginService;
    private Role travelerRole;
    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";
    private static final String WRONG_PASSWORD = UUID.randomUUID() + "Bb2!";

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                userRepository, passwordEncoder, jwtTokenProvider, refreshTokenRepository, tokenHashUtil
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
    @DisplayName("Successful login returns access token and refresh token")
    void login_success() {
        User user = createActiveUser();
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password(USER_PASSWORD)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any())).thenReturn("access-token-123");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token-raw");
        when(tokenHashUtil.hashToken("refresh-token-raw")).thenReturn("hashed-refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = loginService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-123");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-raw");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getTokenHash()).isNotEqualTo("refresh-token-raw");
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
        assertThat(savedToken.getRevoked()).isFalse();
    }

    @Test
    @DisplayName("Login with wrong password throws InvalidCredentialsException")
    void login_wrongPassword_throwsInvalidCredentialsException() {
        User user = createActiveUser();
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password(WRONG_PASSWORD)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(WRONG_PASSWORD, user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login with wrong email throws InvalidCredentialsException")
    void login_wrongEmail_throwsInvalidCredentialsException() {
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password(USER_PASSWORD)
                .build();

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login with deactivated account throws AccountDeactivatedException")
    void login_deactivatedAccount_throwsAccountDeactivatedException() {
        User user = createActiveUser();
        user.setStatus(UserStatus.DEACTIVATED);
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password(USER_PASSWORD)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(AccountDeactivatedException.class)
                .hasMessage("Account has been deactivated");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login with unverified account throws AccountNotVerifiedException")
    void login_unverifiedAccount_throwsAccountNotVerifiedException() {
        User user = createActiveUser();
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerified(false);
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password(USER_PASSWORD)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(AccountNotVerifiedException.class)
                .hasMessage("Email address has not been verified");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login normalizes email to lowercase")
    void login_normalizesEmail() {
        User user = createActiveUser();
        LoginRequest request = LoginRequest.builder()
                .email("  USER@EXAMPLE.COM  ")
                .password(USER_PASSWORD)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any())).thenReturn("token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(tokenHashUtil.hashToken("refresh")).thenReturn("hashed");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = loginService.login(request);

        assertThat(response).isNotNull();
        verify(userRepository).findByEmail("user@example.com");
    }
}
