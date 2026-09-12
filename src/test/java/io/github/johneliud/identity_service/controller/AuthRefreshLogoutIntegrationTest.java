package io.github.johneliud.identity_service.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.johneliud.identity_service.dto.LoginRequest;
import io.github.johneliud.identity_service.dto.RefreshTokenRequest;
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
@Transactional
class AuthRefreshLogoutIntegrationTest {

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

    private Role travelerRole;
    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";

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
    }

    private User createAndSaveUser(String email, UserStatus status, boolean emailVerified) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(USER_PASSWORD))
                .firstName("Test")
                .lastName("User")
                .status(status)
                .emailVerified(emailVerified)
                .roles(new HashSet<>(Set.of(travelerRole)))
                .build();
        return userRepository.save(user);
    }

    private String loginAndGetRefreshToken(String email) throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(USER_PASSWORD)
                .build();

        String response = mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode json = objectMapper.readTree(response);
        return json.get("refreshToken").asString();
    }

    @Test
    @DisplayName("POST /auth/refresh - Success: returns new access and refresh token")
    void refresh_success_returns200WithNewTokens() throws Exception {
        String email = "refresh.success@company.com";
        createAndSaveUser(email, UserStatus.ACTIVE, true);
        String refreshToken = loginAndGetRefreshToken(email);

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-API-Version", "1"))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", notNullValue()));
    }

    @Test
    @DisplayName("POST /auth/refresh - Invalid token returns 401 Unauthorized")
    void refresh_invalidToken_returns401() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("invalid-token-value")
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid or revoked refresh token")));
    }

    @Test
    @DisplayName("POST /auth/refresh - Revoked token returns 401 Unauthorized")
    void refresh_revokedToken_returns401() throws Exception {
        String email = "refresh.revoked@company.com";
        createAndSaveUser(email, UserStatus.ACTIVE, true);
        String refreshToken = loginAndGetRefreshToken(email);

        // First refresh to revoke the original token
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

        // Try to use the same (now revoked) token again
        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid or revoked refresh token")));
    }

    @Test
    @DisplayName("POST /auth/logout - Success: revokes refresh token, returns 204 No Content")
    void logout_success_returns204() throws Exception {
        String email = "logout.success@company.com";
        createAndSaveUser(email, UserStatus.ACTIVE, true);
        String refreshToken = loginAndGetRefreshToken(email);

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/auth/logout")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // Verify the refresh token is now revoked — refresh should fail
        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/logout - Non-existent token returns 204 (idempotent)")
    void logout_nonExistentToken_returns204() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("non-existent-token")
                .build();

        mockMvc.perform(post("/auth/logout")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /auth/refresh - Deactivated account returns 403 Forbidden")
    void refresh_deactivatedAccount_returns403() throws Exception {
        String email = "refresh.deactivated@company.com";
        createAndSaveUser(email, UserStatus.ACTIVE, true);
        String refreshToken = loginAndGetRefreshToken(email);

        // Deactivate the user
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setStatus(UserStatus.DEACTIVATED);
            userRepository.save(user);
        });

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Account has been deactivated")));
    }

    @Test
    @DisplayName("POST /auth/refresh - Validation: missing token returns 400 Bad Request")
    void refresh_validationFailure_returns400() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("")
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")));
    }
}
