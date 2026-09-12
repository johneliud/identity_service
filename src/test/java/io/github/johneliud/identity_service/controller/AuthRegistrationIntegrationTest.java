package io.github.johneliud.identity_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

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

import io.github.johneliud.identity_service.dto.RegisterRequest;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.event.UserRegisteredEvent;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AuthRegistrationIntegrationTest {

    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    @DisplayName("POST /auth/register - Success: 201 Created with Traveler role, hashed password, and version header")
    void register_success_returns201WithTravelerRole() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("traveler.success@company.com")
                .password(USER_PASSWORD)
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/auth/register")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-API-Version", "1"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("traveler.success@company.com")))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.emailVerified", is(false)))
                .andExpect(jsonPath("$.roles", hasItem("TRAVELER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verify persisted state in PostgreSQL database
        Optional<User> savedOpt = userRepository.findByEmail("traveler.success@company.com");
        assertThat(savedOpt).isPresent();
        User saved = savedOpt.get();

        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(saved.getEmailVerified()).isFalse();

        // Verify password is NOT stored in plaintext and is valid BCrypt
        assertThat(saved.getPasswordHash()).isNotEqualTo(USER_PASSWORD);
        assertThat(saved.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(USER_PASSWORD, saved.getPasswordHash())).isTrue();

        // Verify Traveler role association
        assertThat(saved.getRoles()).extracting(Role::getName).contains("TRAVELER");

        // Verify outbox event was dispatched (synchronous, within the transaction)
        verify(outboxEventPublisher, atLeastOnce()).publishUserRegistered(any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("POST /auth/register - Defaults to v1 when X-API-Version header is omitted")
    void register_success_defaultApiVersion() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("traveler.noheader@company.com")
                .password(USER_PASSWORD)
                .firstName("Alice")
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-API-Version", "1"))
                .andExpect(jsonPath("$.email", is("traveler.noheader@company.com")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("POST /auth/register - Accepts 'v1' case-insensitive header alias")
    void register_success_withV1AliasHeader() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("traveler.v1alias@company.com")
                .password(USER_PASSWORD)
                .firstName("Bob")
                .build();

        mockMvc.perform(post("/auth/register")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-API-Version", "1"))
                .andExpect(jsonPath("$.email", is("traveler.v1alias@company.com")));
    }

    @Test
    @DisplayName("POST /auth/register - Duplicate email returns 409 Conflict")
    void register_duplicateEmail_returns409Conflict() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("duplicate@company.com")
                .password(USER_PASSWORD)
                .firstName("First")
                .lastName("User")
                .build();

        // First registration
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Attempt second registration with same email (uppercase to test case insensitivity)
        RegisterRequest duplicateRequest = RegisterRequest.builder()
                .email("DUPLICATE@company.com")
                .password(USER_PASSWORD)
                .firstName("Second")
                .lastName("User")
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("User with email 'duplicate@company.com' already exists")))
                .andExpect(jsonPath("$.path", is("/auth/register")));
    }

    @Test
    @DisplayName("POST /auth/register - Validation: Invalid input returns 400 Bad Request with field errors")
    void register_invalidInput_returns400BadRequest() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .email("not-an-email")
                .password("weak")
                .firstName("")
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()))
                .andExpect(jsonPath("$.errors.firstName", notNullValue()));

        verify(outboxEventPublisher, never()).publishUserRegistered(any());
    }

    @Test
    @DisplayName("POST /auth/register - Versioning: Unsupported API version returns 400 Bad Request")
    void register_unsupportedApiVersion_returns400BadRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("version.test@company.com")
                .password(USER_PASSWORD)
                .firstName("Version")
                .build();

        mockMvc.perform(post("/auth/register")
                        .header("X-API-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Unsupported API version: '2'. Supported versions: 1")));
    }

    @Test
    @DisplayName("POST /auth/register - Malformed JSON body returns 400 Bad Request")
    void register_malformedJson_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Malformed JSON request body")));
    }
}
