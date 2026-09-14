package io.github.johneliud.identity_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ForgotPasswordRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid forgot password request passes validation")
    void validRequest_passesValidation() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("user@example.com")
                .build();
        Set<ConstraintViolation<ForgotPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null email fails validation")
    void nullEmail_failsValidation() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(null)
                .build();
        Set<ConstraintViolation<ForgotPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email"})
    @DisplayName("Invalid email fails validation")
    void invalidEmail_failsValidation(String email) {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(email)
                .build();
        Set<ConstraintViolation<ForgotPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }
}
