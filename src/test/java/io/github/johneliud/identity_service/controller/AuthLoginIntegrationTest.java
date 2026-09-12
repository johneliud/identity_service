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
class AuthLoginIntegrationTest {

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

    @Test
    @DisplayName("POST /auth/login - Success: returns access token and refresh token")
    void login_success_returns200WithTokens() throws Exception {
        createAndSaveUser("login.success@company.com", UserStatus.ACTIVE, true);

        LoginRequest request = LoginRequest.builder()
                .email("login.success@company.com")
                .password(USER_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
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
    @DisplayName("POST /auth/login - Wrong password returns 401 Unauthorized")
    void login_wrongPassword_returns401() throws Exception {
        createAndSaveUser("login.wrongpass@company.com", UserStatus.ACTIVE, true);

        LoginRequest request = LoginRequest.builder()
                .email("login.wrongpass@company.com")
                .password("WrongPassword123!")
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("POST /auth/login - Non-existent email returns 401 Unauthorized")
    void login_nonExistentEmail_returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@company.com")
                .password("SomePassword123!")
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("POST /auth/login - Deactivated account returns 403 Forbidden")
    void login_deactivatedAccount_returns403() throws Exception {
        createAndSaveUser("login.deactivated@company.com", UserStatus.DEACTIVATED, false);

        LoginRequest request = LoginRequest.builder()
                .email("login.deactivated@company.com")
                .password(USER_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Account has been deactivated")));
    }

    @Test
    @DisplayName("POST /auth/login - Unverified account returns 403 Forbidden")
    void login_unverifiedAccount_returns403() throws Exception {
        createAndSaveUser("login.unverified@company.com", UserStatus.PENDING, false);

        LoginRequest request = LoginRequest.builder()
                .email("login.unverified@company.com")
                .password(USER_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Email address has not been verified")));
    }

    @Test
    @DisplayName("POST /auth/login - Validation: missing fields returns 400 Bad Request")
    void login_validationFailure_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("")
                .password("")
                .build();

        mockMvc.perform(post("/auth/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Validation failed")));
    }

    @Test
    @DisplayName("POST /auth/login - Defaults to v1 when X-API-Version header is omitted")
    void login_success_defaultApiVersion() throws Exception {
        createAndSaveUser("login.noheader@company.com", UserStatus.ACTIVE, true);

        LoginRequest request = LoginRequest.builder()
                .email("login.noheader@company.com")
                .password(USER_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-API-Version", "1"))
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }
}
