package io.github.johneliud.identity_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class LoginRequestValidationTest {

    private static Validator validator;
    private static final String VALID_PASSWORD = UUID.randomUUID() + "Aa1!";

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private LoginRequest createValidRequest() {
        return LoginRequest.builder()
                .email("user@example.com")
                .password(VALID_PASSWORD)
                .build();
    }

    @Test
    @DisplayName("Valid login request passes bean validation")
    void validRequest_passesValidation() {
        LoginRequest request = createValidRequest();
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email", "missing-at-sign.com", "@missingusername.com"})
    @DisplayName("Invalid email formats fail validation")
    void invalidEmail_failsValidation(String email) {
        LoginRequest request = createValidRequest();
        request.setEmail(email);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("Null email fails validation")
    void nullEmail_failsValidation() {
        LoginRequest request = createValidRequest();
        request.setEmail(null);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("Null password fails validation")
    void nullPassword_failsValidation() {
        LoginRequest request = createValidRequest();
        request.setPassword(null);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Blank password fails validation")
    void blankPassword_failsValidation(String password) {
        LoginRequest request = createValidRequest();
        request.setPassword(password);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("LoginRequest.toString() does not expose plaintext password")
    void toString_excludesPlaintextPassword() {
        LoginRequest request = createValidRequest();
        String stringRepresentation = request.toString();
        assertThat(stringRepresentation).doesNotContain(VALID_PASSWORD);
    }
}
