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
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private EmailVerificationService emailVerificationService;

    private RegistrationService registrationServiceWithVerification;
    private RegistrationService registrationServiceWithoutVerification;
    private Role travelerRole;
    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";

    @BeforeEach
    void setUp() {
        registrationServiceWithVerification = new RegistrationService(
                userRepository, roleRepository, passwordEncoder, outboxEventPublisher,
                emailVerificationService, true, true
        );
        registrationServiceWithoutVerification = new RegistrationService(
                userRepository, roleRepository, passwordEncoder, outboxEventPublisher,
                emailVerificationService, false, false
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

        when(emailVerificationService.generateToken(any(User.class))).thenReturn("dev-verification-token-abc123");

        UserResponse response = registrationServiceWithVerification.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(generatedId);
        assertThat(response.getEmail()).isEqualTo("newuser@example.com");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(response.getEmailVerified()).isFalse();
        assertThat(response.getRoles()).containsExactly("TRAVELER");
        assertThat(response.getVerificationToken()).isEqualTo("dev-verification-token-abc123");

        verify(emailVerificationService).generateToken(any(User.class));

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

        UserResponse response = registrationServiceWithoutVerification.register(request);

        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getEmailVerified()).isTrue();
        assertThat(response.getVerificationToken()).isNull();
        verify(emailVerificationService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Duplicate email throws UserAlreadyExistsException and aborts registration")
    void register_duplicateEmail_throwsUserAlreadyExistsException() {
        RegisterRequest request = createRegisterRequest();
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationServiceWithVerification.register(request))
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

        UserResponse response = registrationServiceWithVerification.register(request);
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

        assertThatThrownBy(() -> registrationServiceWithVerification.register(request))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessageContaining("TRAVELER");

        verify(userRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishUserRegistered(any());
    }
}
