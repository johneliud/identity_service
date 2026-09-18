package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import io.github.johneliud.identity_service.dto.UpdateProfileRequest;
import io.github.johneliud.identity_service.dto.UserProfileResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.InvalidCredentialsException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private UserProfileService userProfileService;
    private Role travelerRole;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userRepository, outboxEventPublisher);

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
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("getProfile returns user profile data")
    void getProfile_success() {
        User user = createActiveUser();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse response = userProfileService.getProfile(user.getId());

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getEmailVerified()).isTrue();
        assertThat(response.getRoles()).contains("TRAVELER");
    }

    @Test
    @DisplayName("getProfile throws for non-existent user")
    void getProfile_userNotFound_throwsInvalidCredentials() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile(unknownId))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("updateProfile updates first name and last name")
    void updateProfile_success() {
        User user = createActiveUser();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .build();

        UserProfileResponse response = userProfileService.updateProfile(user.getId(), request);

        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Smith");
        verify(userRepository).save(any(User.class));
        verify(outboxEventPublisher).publishUserUpdated(any(User.class), any(String.class));
    }

    @Test
    @DisplayName("updateProfile allows empty last name to clear it")
    void updateProfile_clearLastName() {
        User user = createActiveUser();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("John")
                .lastName("")
                .build();

        UserProfileResponse response = userProfileService.updateProfile(user.getId(), request);

        assertThat(response.getLastName()).isEmpty();
    }

    @Test
    @DisplayName("updateProfile does not change first name if null")
    void updateProfile_nullFirstName_unchanged() {
        User user = createActiveUser();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .lastName("NewLast")
                .build();

        UserProfileResponse response = userProfileService.updateProfile(user.getId(), request);

        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("NewLast");
    }

    @Test
    @DisplayName("updateProfile throws for non-existent user")
    void updateProfile_userNotFound_throwsInvalidCredentials() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Jane")
                .build();

        assertThatThrownBy(() -> userProfileService.updateProfile(unknownId, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("User not found");
    }
}
