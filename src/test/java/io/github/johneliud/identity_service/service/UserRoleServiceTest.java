package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.RoleAlreadyAssignedException;
import io.github.johneliud.identity_service.exception.RoleNotAssignedException;
import io.github.johneliud.identity_service.exception.RoleNotFoundException;
import io.github.johneliud.identity_service.exception.UserNotFoundException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private UserRoleService userRoleService;

    private User testUser;
    private Role adminRole;
    private Role travelerRole;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userRoleService = new UserRoleService(userRepository, roleRepository, outboxEventPublisher);

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
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(travelerRole)))
                .build();
    }

    @Test
    @DisplayName("Should add role to user")
    void shouldAddRoleToUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        userRoleService.changeRole(userId, "ADMIN", "ADD");

        verify(userRepository).save(testUser);
        verify(outboxEventPublisher).publishUserUpdated(testUser, "ROLE_CHANGED");
    }

    @Test
    @DisplayName("Should remove role from user")
    void shouldRemoveRoleFromUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("TRAVELER")).thenReturn(Optional.of(travelerRole));

        userRoleService.changeRole(userId, "TRAVELER", "REMOVE");

        verify(userRepository).save(testUser);
        verify(outboxEventPublisher).publishUserUpdated(testUser, "ROLE_CHANGED");
    }

    @Test
    @DisplayName("Should throw when user already has role")
    void shouldThrowWhenRoleAlreadyAssigned() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("TRAVELER")).thenReturn(Optional.of(travelerRole));

        assertThatThrownBy(() -> userRoleService.changeRole(userId, "TRAVELER", "ADD"))
                .isInstanceOf(RoleAlreadyAssignedException.class)
                .hasMessageContaining("User already has role");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when user does not have role to remove")
    void shouldThrowWhenRoleNotAssigned() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        assertThatThrownBy(() -> userRoleService.changeRole(userId, "ADMIN", "REMOVE"))
                .isInstanceOf(RoleNotAssignedException.class)
                .hasMessageContaining("User does not have role");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when user not found")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoleService.changeRole(userId, "ADMIN", "ADD"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when role not found")
    void shouldThrowWhenRoleNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoleService.changeRole(userId, "SUPER_ADMIN", "ADD"))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessageContaining("Role not found");

        verify(userRepository, never()).save(any());
    }
}
