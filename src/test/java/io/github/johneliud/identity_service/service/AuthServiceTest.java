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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.johneliud.identity_service.config.JwtTokenProvider;
import io.github.johneliud.identity_service.dto.LoginRequest;
import io.github.johneliud.identity_service.dto.LoginResponse;
import io.github.johneliud.identity_service.dto.RefreshTokenRequest;
import io.github.johneliud.identity_service.dto.RefreshTokenResponse;
import io.github.johneliud.identity_service.dto.RegisterRequest;
import io.github.johneliud.identity_service.dto.UserResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.event.UserRegisteredEvent;
import io.github.johneliud.identity_service.exception.AccountDeactivatedException;
import io.github.johneliud.identity_service.exception.AccountNotVerifiedException;
import io.github.johneliud.identity_service.exception.InvalidCredentialsException;
import io.github.johneliud.identity_service.exception.InvalidRefreshTokenException;
import io.github.johneliud.identity_service.exception.RoleNotFoundException;
import io.github.johneliud.identity_service.exception.UserAlreadyExistsException;
import io.github.johneliud.identity_service.model.RefreshToken;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RefreshTokenRepository;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authServiceWithVerification;
    private AuthService authServiceWithoutVerification;
    private Role travelerRole;
    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";

    @BeforeEach
    void setUp() {
        authServiceWithVerification = new AuthService(
                userRepository, roleRepository, passwordEncoder, outboxEventPublisher,
                jwtTokenProvider, refreshTokenRepository, true
        );
        authServiceWithoutVerification = new AuthService(
                userRepository, roleRepository, passwordEncoder, outboxEventPublisher,
                jwtTokenProvider, refreshTokenRepository, false
        );

        travelerRole = Role.builder()
                .id(1L)
                .name("TRAVELER")
                .description("Corporate traveler")
                .build();
    }

    private RegisterRequest createRegisterRequest() {
        return RegisterRequest.builder()
                .email("newuser@example.com")
                .password(USER_PASSWORD)
                .firstName("Alice")
                .lastName("Smith")
                .build();
    }

    @Test
    @DisplayName("Successful registration creates user with PENDING status when email verification is required")
    void register_success_emailVerificationRequired() {
        RegisterRequest request = createRegisterRequest();
        String hashedPassword = "$2a$12$hashedPasswordExample";

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(roleRepository.findByName("TRAVELER")).thenReturn(Optional.of(travelerRole));
        when(passwordEncoder.encode(USER_PASSWORD)).thenReturn(hashedPassword);

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(generatedId);
            return user;
        });

        UserResponse response = authServiceWithVerification.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(generatedId);
        assertThat(response.getEmail()).isEqualTo("newuser@example.com");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(response.getEmailVerified()).isFalse();
        assertThat(response.getRoles()).containsExactly("TRAVELER");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo(hashedPassword);
        assertThat(saved.getPasswordHash()).isNotEqualTo(USER_PASSWORD);
        assertThat(saved.getEmail()).isEqualTo("newuser@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(saved.getEmailVerified()).isFalse();

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(outboxEventPublisher).publishUserRegistered(eventCaptor.capture());
        UserRegisteredEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(generatedId);
        assertThat(event.email()).isEqualTo("newuser@example.com");
        assertThat(event.firstName()).isEqualTo("Alice");
        assertThat(event.lastName()).isEqualTo("Smith");
        assertThat(event.status()).isEqualTo("PENDING");
        assertThat(event.roles()).containsExactly("TRAVELER");
    }

    @Test
    @DisplayName("Successful registration creates user with ACTIVE status when email verification is disabled")
    void register_success_emailVerificationNotRequired() {
        RegisterRequest request = createRegisterRequest();

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(roleRepository.findByName("TRAVELER")).thenReturn(Optional.of(travelerRole));
        when(passwordEncoder.encode(USER_PASSWORD)).thenReturn("hashedPass");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserResponse response = authServiceWithoutVerification.register(request);

        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Duplicate email throws UserAlreadyExistsException and aborts registration")
    void register_duplicateEmail_throwsUserAlreadyExistsException() {
        RegisterRequest request = createRegisterRequest();
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authServiceWithVerification.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("newuser@example.com");

        verify(roleRepository, never()).findByName(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishUserRegistered(any());
    }

    @Test
    @DisplayName("Registration normalizes email to lowercase")
    void register_normalizesEmail() {
        RegisterRequest request = createRegisterRequest();
        request.setEmail("  Alice.Smith@Example.COM  ");

        when(userRepository.existsByEmail("alice.smith@example.com")).thenReturn(false);
        when(roleRepository.findByName("TRAVELER")).thenReturn(Optional.of(travelerRole));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserResponse response = authServiceWithVerification.register(request);
        assertThat(response.getEmail()).isEqualTo("alice.smith@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice.smith@example.com");
    }

    @Test
    @DisplayName("Missing default role TRAVELER throws RoleNotFoundException")
    void register_missingRole_throwsRoleNotFoundException() {
        RegisterRequest request = createRegisterRequest();
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(roleRepository.findByName("TRAVELER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServiceWithVerification.register(request))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessageContaining("TRAVELER");

        verify(userRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishUserRegistered(any());
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
        when(jwtTokenProvider.generateAccessToken(anyString(),  ArgumentMatchers.<Set<String>>any())).thenReturn("access-token-123");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token-raw");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authServiceWithVerification.login(request);

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
                .password("WrongPass123!")
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass123!", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authServiceWithVerification.login(request))
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

        assertThatThrownBy(() -> authServiceWithVerification.login(request))
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

        assertThatThrownBy(() -> authServiceWithVerification.login(request))
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

        assertThatThrownBy(() -> authServiceWithVerification.login(request))
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
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authServiceWithVerification.login(request);

        assertThat(response).isNotNull();
        verify(userRepository).findByEmail("user@example.com");
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

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(existingToken));
        when(jwtTokenProvider.generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any()))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenResponse response = authServiceWithVerification.refresh(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-raw-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        // Verify old token was revoked
        assertThat(existingToken.getRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh with non-existent token throws InvalidRefreshTokenException")
    void refresh_nonExistentToken_throwsInvalidRefreshTokenException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("non-existent-token")
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServiceWithVerification.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or revoked refresh token");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
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

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authServiceWithVerification.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token has expired");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
    }

    @Test
    @DisplayName("Refresh with revoked token throws InvalidRefreshTokenException")
    void refresh_revokedToken_throwsInvalidRefreshTokenException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("revoked-raw-token")
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServiceWithVerification.refresh(request))
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

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(existingToken));

        assertThatThrownBy(() -> authServiceWithVerification.refresh(request))
                .isInstanceOf(AccountDeactivatedException.class)
                .hasMessage("Account has been deactivated");

        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), ArgumentMatchers.<Set<String>>any());
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

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(existingToken));

        authServiceWithVerification.logout(request);

        assertThat(existingToken.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(existingToken);
    }

    @Test
    @DisplayName("Logout with non-existent token does not throw")
    void logout_nonExistentToken_doesNotThrow() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("non-existent-token")
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        authServiceWithVerification.logout(request);

        verify(refreshTokenRepository, never()).save(any());
    }
}
