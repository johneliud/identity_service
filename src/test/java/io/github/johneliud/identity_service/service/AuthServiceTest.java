package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.johneliud.identity_service.dto.RegisterRequest;
import io.github.johneliud.identity_service.dto.UserResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.event.UserRegisteredEvent;
import io.github.johneliud.identity_service.exception.RoleNotFoundException;
import io.github.johneliud.identity_service.exception.UserAlreadyExistsException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
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

    private AuthService authServiceWithVerification;
    private AuthService authServiceWithoutVerification;
    private Role travelerRole;

    @BeforeEach
    void setUp() {
        authServiceWithVerification = new AuthService(
                userRepository, roleRepository, passwordEncoder, outboxEventPublisher, true
        );
        authServiceWithoutVerification = new AuthService(
                userRepository, roleRepository, passwordEncoder, outboxEventPublisher, false
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
                .password("SecurePass123!")
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
        when(passwordEncoder.encode("SecurePass123!")).thenReturn(hashedPassword);

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

        // Verify entity state saved to DB
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo(hashedPassword);
        assertThat(saved.getPasswordHash()).isNotEqualTo("SecurePass123!");
        assertThat(saved.getEmail()).isEqualTo("newuser@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(saved.getEmailVerified()).isFalse();

        // Verify outbox event persisted (outbox pattern — not direct Kafka publish)
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
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("hashedPass");

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
}
