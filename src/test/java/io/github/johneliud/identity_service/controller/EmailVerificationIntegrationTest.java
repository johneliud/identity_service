package io.github.johneliud.identity_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import io.github.johneliud.identity_service.dto.RegisterRequest;
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
class EmailVerificationIntegrationTest {

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

    private String registerAndGetVerificationToken(String email) throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(USER_PASSWORD)
                .firstName("Verify")
                .lastName("User")
                .build();

        String response = mockMvc.perform(post("/auth/register")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode json = objectMapper.readTree(response);
        return json.get("verificationToken").asString();
    }

    @Test
    @DisplayName("GET /auth/verify-email - Success: activates account and marks email_verified = true")
    void verifyEmail_success_returns200() throws Exception {
        String email = "verify.success@company.com";
        String verificationToken = registerAndGetVerificationToken(email);

        mockMvc.perform(get("/auth/verify-email")
                        .header("X-API-Version", "1")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        // Verify user is now ACTIVE and email_verified = true
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("GET /auth/verify-email - Invalid token returns 400 Bad Request")
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(get("/auth/verify-email")
                        .header("X-API-Version", "1")
                        .param("token", "invalid-token-value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Invalid or already used verification token")));
    }

    @Test
    @DisplayName("GET /auth/verify-email - Already used token returns 400 Bad Request")
    void verifyEmail_alreadyUsedToken_returns400() throws Exception {
        String email = "verify.used@company.com";
        String verificationToken = registerAndGetVerificationToken(email);

        // First verification succeeds
        mockMvc.perform(get("/auth/verify-email")
                        .header("X-API-Version", "1")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        // Second verification with same token fails
        mockMvc.perform(get("/auth/verify-email")
                        .header("X-API-Version", "1")
                        .param("token", verificationToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid or already used verification token")));
    }

    @Test
    @DisplayName("GET /auth/verify-email - Missing token parameter returns 400 Bad Request")
    void verifyEmail_missingToken_returns400() throws Exception {
        mockMvc.perform(get("/auth/verify-email")
                        .header("X-API-Version", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Missing required parameter: 'token'")));
    }

    @Test
    @DisplayName("GET /auth/verify-email - Verified account can log in")
    void verifyEmail_thenLogin_succeeds() throws Exception {
        String email = "verify.login@company.com";
        String verificationToken = registerAndGetVerificationToken(email);

        // Verify email
        mockMvc.perform(get("/auth/verify-email")
                        .header("X-API-Version", "1")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        // Now login should succeed
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(USER_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    @DisplayName("GET /auth/verify-email - Unverified account cannot log in")
    void verifyEmail_unverifiedAccount_cannotLogin() throws Exception {
        String email = "verify.nologin@company.com";
        registerAndGetVerificationToken(email);

        // Login should fail without verification
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(USER_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Email address has not been verified")));
    }
}
