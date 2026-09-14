package io.github.johneliud.identity_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.UserRepository;

@SpringBootTest
@Testcontainers
@Transactional
class DataInitializerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private OutboxEventPublisher outboxEventPublisher;

    private static final String ADMIN_PASSWORD = UUID.randomUUID() + "Aa1!";

    @DynamicPropertySource
        static void overrideProperties(DynamicPropertyRegistry registry) {
            registry.add("identity.admin.email", () -> "admin@company.com");
            registry.add("identity.admin.password", () -> ADMIN_PASSWORD);
            registry.add("identity.admin.first-name", () -> "System");
            registry.add("identity.admin.last-name", () -> "Administrator");
        }

    @Test
    @DisplayName("DataInitializer creates admin user on startup from application-secrets.properties")
    void dataInitializerCreatesAdminUser() {
        User admin = userRepository.findByEmail("admin@company.com").orElseThrow();

        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getEmailVerified()).isTrue();
        assertThat(admin.getFirstName()).isEqualTo("System");
        assertThat(admin.getLastName()).isEqualTo("Administrator");
        assertThat(admin.getRoles()).hasSize(1);
        assertThat(admin.getRoles().iterator().next().getName()).isEqualTo("ADMIN");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }
}
