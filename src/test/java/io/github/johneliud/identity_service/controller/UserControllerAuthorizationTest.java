package io.github.johneliud.identity_service.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import io.github.johneliud.identity_service.dto.UpdateRoleRequest;
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
class UserControllerAuthorizationTest {

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
    private Role travelerRole;
    private Role adminRole;
    private UUID userId;

    @BeforeEach
    void setUp() {
        travelerRole = roleRepository.findByName("TRAVELER")
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name("TRAVELER")
                            .description("Corporate traveler")
                            .build();
                    return roleRepository.save(role);
                });

        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name("ADMIN")
                            .description("System administrator")
                            .build();
                    return roleRepository.save(role);
                });

        testUser = User.builder()
                .email("test-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("Str0ng!Pass1"))
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(travelerRole)))
                .build();
        testUser = userRepository.save(testUser);
        userId = testUser.getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("ADMIN can add role to user")
    void adminCanAddRole() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ADMIN can remove role from user")
    void adminCanRemoveRole() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("TRAVELER", "REMOVE");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TRAVEL_MANAGER gets 403 when changing roles")
    void travelManagerGetsForbidden() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "TRAVEL_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Access denied: insufficient permissions")));
    }

    @Test
    @DisplayName("TRAVELER gets 403 when changing roles")
    void travelerGetsForbidden() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "TRAVELER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Access denied: insufficient permissions")));
    }

    @Test
    @DisplayName("Unauthenticated request gets 403")
    void unauthenticatedGetsForbidden() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Non-existent user returns 404")
    void nonExistentUserReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", nonExistentId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found: " + nonExistentId)));
    }

    @Test
    @DisplayName("User already has role returns 409")
    void roleAlreadyAssignedReturns409() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("TRAVELER", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("User already has role: TRAVELER")));
    }

    @Test
    @DisplayName("Invalid role name returns 400")
    void invalidRoleNameReturns400() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("SUPER_ADMIN", "ADD");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid action returns 400")
    void invalidActionReturns400() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "DELETE");

        mockMvc.perform(patch("/users/{id}/roles", userId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
