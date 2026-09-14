package io.github.johneliud.identity_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AdminUserControllerAuthorizationTest {

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

    private String adminHeaders() {
        return adminUser.getId().toString();
    }

    @Test
    @DisplayName("ADMIN can list users")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", notNullValue()));
    }

    @Test
    @DisplayName("ADMIN can filter users by status")
    void adminCanFilterUsersByStatus() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    @DisplayName("ADMIN can filter users by role")
    void adminCanFilterUsersByRole() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .param("role", "TRAVELER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    @DisplayName("ADMIN can filter users by email")
    void adminCanFilterUsersByEmail() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .param("email", "user-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    @DisplayName("ADMIN can get user detail")
    void adminCanGetUserDetail() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", testUser.getId())
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.email", is(testUser.getEmail())))
                .andExpect(jsonPath("$.firstName", is("Test")))
                .andExpect(jsonPath("$.lastName", is("User")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.emailVerified", is(true)))
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    @DisplayName("ADMIN can deactivate user")
    void adminCanDeactivateUser() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("DEACTIVATED");

        mockMvc.perform(patch("/admin/users/{id}/status", testUser.getId())
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ADMIN can reactivate user")
    void adminCanReactivateUser() throws Exception {
        testUser.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(testUser);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest("ACTIVE");

        mockMvc.perform(patch("/admin/users/{id}/status", testUser.getId())
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TRAVELER gets 403 on list users")
    void travelerGets403OnListUsers() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("X-User-Id", testUser.getId().toString())
                        .header("X-User-Roles", "TRAVELER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TRAVELER gets 403 on get user detail")
    void travelerGets403OnGetUserDetail() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", testUser.getId())
                        .header("X-User-Id", testUser.getId().toString())
                        .header("X-User-Roles", "TRAVELER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TRAVELER gets 403 on update status")
    void travelerGets403OnUpdateStatus() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("DEACTIVATED");

        mockMvc.perform(patch("/admin/users/{id}/status", testUser.getId())
                        .header("X-User-Id", testUser.getId().toString())
                        .header("X-User-Roles", "TRAVELER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request gets 403")
    void unauthenticatedGets403() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Non-existent user returns 404")
    void nonExistentUserReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/admin/users/{id}", nonExistentId)
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found: " + nonExistentId)));
    }

    @Test
    @DisplayName("Invalid status returns 400")
    void invalidStatusReturns400() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("PENDING");

        mockMvc.perform(patch("/admin/users/{id}/status", testUser.getId())
                        .header("X-User-Id", adminHeaders())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
