package io.github.johneliud.identity_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;

@SpringBootTest
@Testcontainers
@Transactional
class DatabaseMigrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Acceptance Criteria: Flyway migrations are applied successfully")
    void testFlywayMigrationsApplied() {
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
        );

        assertThat(migrations).hasSizeGreaterThanOrEqualTo(3);
        assertThat(migrations.get(0).get("version")).isEqualTo("1");
        assertThat(migrations.get(0).get("description")).isEqualTo("init schema");
        assertThat(migrations.get(0).get("success")).isEqualTo(true);

        assertThat(migrations.get(1).get("version")).isEqualTo("2");
        assertThat(migrations.get(1).get("description")).isEqualTo("seed roles");
        assertThat(migrations.get(1).get("success")).isEqualTo(true);

        assertThat(migrations.get(2).get("version")).isEqualTo("3");
        assertThat(migrations.get(2).get("description")).isEqualTo("add user names");
        assertThat(migrations.get(2).get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Acceptance Criteria: Roles table seeded with ADMIN, TRAVEL_MANAGER, TRAVELER")
    void testDefaultRolesSeeded() {
        assertThat(roleRepository.existsByName("ADMIN")).isTrue();
        assertThat(roleRepository.existsByName("TRAVEL_MANAGER")).isTrue();
        assertThat(roleRepository.existsByName("TRAVELER")).isTrue();

        Optional<Role> adminRole = roleRepository.findByName("ADMIN");
        assertThat(adminRole).isPresent();
        assertThat(adminRole.get().getDescription()).isNotBlank();
        assertThat(adminRole.get().getPermissions()).isNotEmpty();

        Optional<Role> managerRole = roleRepository.findByName("TRAVEL_MANAGER");
        assertThat(managerRole).isPresent();
        assertThat(managerRole.get().getPermissions()).isNotEmpty();

        Optional<Role> travelerRole = roleRepository.findByName("TRAVELER");
        assertThat(travelerRole).isPresent();
        assertThat(travelerRole.get().getPermissions()).isNotEmpty();
    }

    @Test
    @DisplayName("Acceptance Criteria: User creation with id, email, password_hash, first_name, last_name, status, created_at, updated_at, email_verified")
    void testCreateUserWithAllFields() {
        User user = User.builder()
                .email("traveler@company.com")
                .passwordHash("$2a$12$e8Y...hash")
                .firstName("John")
                .lastName("Doe")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("traveler@company.com");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$e8Y...hash");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getEmailVerified()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Acceptance Criteria: Last name is optional for User")
    void testCreateUserWithOptionalLastName() {
        User user = User.builder()
                .email("single-name@company.com")
                .passwordHash("secret-hash")
                .firstName("Aristotle")
                .lastName(null)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("Aristotle");
        assertThat(saved.getLastName()).isNull();

        User retrieved = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getFirstName()).isEqualTo("Aristotle");
        assertThat(retrieved.getLastName()).isNull();
    }

    @Test
    @DisplayName("Acceptance Criteria: User roles join table supports multi-role users")
    void testUserRolesJoinTableMultiRole() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role travelerRole = roleRepository.findByName("TRAVELER").orElseThrow();

        User dualRoleUser = User.builder()
                .email("admin-traveler@company.com")
                .passwordHash("hashed-pw")
                .firstName("Jane")
                .lastName("Admin")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(Set.of(adminRole, travelerRole))
                .build();

        User saved = userRepository.saveAndFlush(dualRoleUser);
        assertThat(saved.getId()).isNotNull();

        // Verify join table in database
        List<Map<String, Object>> joinRows = jdbcTemplate.queryForList(
                "SELECT role_id FROM user_roles WHERE user_id = ?",
                saved.getId()
        );
        assertThat(joinRows).hasSize(2);

        // Fetch back and check roles
        User retrieved = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getRoles()).hasSize(2);
        assertThat(retrieved.getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("ADMIN", "TRAVELER");
    }

    @Test
    @DisplayName("Acceptance Criteria: Constraint - Unique email column enforced")
    void testUniqueEmailConstraint() {
        User first = User.builder()
                .email("unique-test@company.com")
                .passwordHash("hash1")
                .firstName("First")
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();
        userRepository.saveAndFlush(first);

        User duplicate = User.builder()
                .email("unique-test@company.com")
                .passwordHash("hash2")
                .firstName("Second")
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Acceptance Criteria: Constraint - Non-null password_hash enforced")
    void testNonNullPasswordHashConstraint() {
        assertThatThrownBy(() -> {
            jdbcTemplate.execute(
                    "INSERT INTO users (email, password_hash, first_name, status) VALUES ('null-pw@test.com', NULL, 'Test', 'ACTIVE')"
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Acceptance Criteria: Constraint - Non-null first_name enforced")
    void testNonNullFirstNameConstraint() {
        assertThatThrownBy(() -> {
            jdbcTemplate.execute(
                    "INSERT INTO users (email, password_hash, first_name, status) VALUES ('null-fname@test.com', 'hash', NULL, 'ACTIVE')"
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Acceptance Criteria: Constraint - Status check constraint (ACTIVE, PENDING, DEACTIVATED)")
    void testStatusCheckConstraint() {
        assertThatThrownBy(() -> {
            jdbcTemplate.execute(
                    "INSERT INTO users (email, password_hash, first_name, status) VALUES ('invalid-status@test.com', 'hash', 'Test', 'INVALID_STATUS')"
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Acceptance Criteria: Indexed email column exists in PostgreSQL schema")
    void testEmailColumnIsIndexed() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_email'"
        );

        assertThat(indexes).isNotEmpty();
        assertThat((String) indexes.get(0).get("indexdef")).contains("users USING btree (email)");
    }
}
