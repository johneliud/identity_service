package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import io.github.johneliud.identity_service.dto.AdminUserListResponse;
import io.github.johneliud.identity_service.dto.UserDetailResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.UserNotFoundException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private AdminUserService adminUserService;

    private User testUser;
    private Role adminRole;
    private Role travelerRole;
    private UUID userId;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, outboxEventPublisher);

        userId = UUID.randomUUID();
        adminRole = Role.builder()
                .id(1L)
                .name("ADMIN")
                .description("System administrator")
                .build();
        travelerRole = Role.builder()
                .id(2L)
                .name("TRAVELER")
                .description("Corporate traveler")
                .build();

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("hashed")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(adminRole, travelerRole)))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("listUsers returns paginated results")
    void listUsersReturnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Page<User> page = new PageImpl<>(List.of(testUser), pageable, 1);
        when(userRepository.findAllFiltered(null, null, null, pageable))
                .thenReturn(page);

        AdminUserListResponse response = adminUserService.listUsers(0, 20, null, null, null);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("getUser returns user detail")
    void getUserReturnsUserDetail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserDetailResponse response = adminUserService.getUser(userId);

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFirstName()).isEqualTo("Test");
        assertThat(response.getLastName()).isEqualTo("User");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getEmailVerified()).isTrue();
        assertThat(response.getRoles()).containsExactlyInAnyOrder("ADMIN", "TRAVELER");
    }

    @Test
    @DisplayName("getUser throws when user not found")
    void getUserThrowsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("updateUserStatus deactivates user")
    void updateUserStatusDeactivatesUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        adminUserService.updateUserStatus(userId, UserStatus.DEACTIVATED);

        assertThat(testUser.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userRepository).save(testUser);
        verify(outboxEventPublisher).publishUserUpdated(testUser, "USER_DEACTIVATED");
    }

    @Test
    @DisplayName("updateUserStatus reactivates user")
    void updateUserStatusReactivatesUser() {
        testUser.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        adminUserService.updateUserStatus(userId, UserStatus.ACTIVE);

        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(testUser);
        verify(outboxEventPublisher).publishUserUpdated(testUser, "USER_REACTIVATED");
    }

    @Test
    @DisplayName("updateUserStatus throws when user not found")
    void updateUserStatusThrowsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateUserStatus(userId, UserStatus.ACTIVE))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).save(any());
    }
}
