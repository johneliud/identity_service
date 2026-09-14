package io.github.johneliud.identity_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.johneliud.identity_service.dto.UpdateUserStatusRequest;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminUserEventPublishingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OutboxEventPublisher outboxEventPublisher;

    private User testUser;
    private User adminUser;
    private Role adminRole;
    private Role travelerRole;

    @BeforeEach
    void setUp() {
        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name("ADMIN")
                            .description("System administrator")
                            .build();
                    return roleRepository.save(role);
                });

        travelerRole = roleRepository.findByName("TRAVELER")
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name("TRAVELER")
                            .description("Corporate traveler")
                            .build();
                    return roleRepository.save(role);
                });

        testUser = User.builder()
                .email("user-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("Str0ng!Pass1"))
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(travelerRole)))
                .build();
        testUser = userRepository.save(testUser);

        adminUser = User.builder()
                .email("admin-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("Str0ng!Pass1"))
                .firstName("Admin")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(adminRole)))
                .build();
        adminUser = userRepository.save(adminUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(testUser.getId());
        userRepository.deleteById(adminUser.getId());
    }

    @Test
    @DisplayName("Deactivating a user publishes USER_DEACTIVATED event")
    void deactivatingUser_publishesEvent() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("DEACTIVATED");

        mockMvc.perform(patch("/admin/users/{id}/status", testUser.getId())
                        .header("X-User-Id", adminUser.getId().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(outboxEventPublisher, atLeastOnce())
                .publishUserUpdated(any(User.class), any(String.class));
    }

    @Test
    @DisplayName("Reactivating a user publishes USER_REACTIVATED event")
    void reactivatingUser_publishesEvent() throws Exception {
        testUser.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(testUser);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest("ACTIVE");

        mockMvc.perform(patch("/admin/users/{id}/status", testUser.getId())
                        .header("X-User-Id", adminUser.getId().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(outboxEventPublisher, atLeastOnce())
                .publishUserUpdated(any(User.class), any(String.class));
    }
}
