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

class ResetPasswordRequestValidationTest {

    private static Validator validator;
    private static final String NEW_PASSWORD = UUID.randomUUID() + "Bb2!";

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid reset password request passes validation")
    void validRequest_passesValidation() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("123456")
                .newPassword(NEW_PASSWORD)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null code fails validation")
    void nullCode_failsValidation() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code(null)
                .newPassword(NEW_PASSWORD)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    @DisplayName("Non-numeric code fails validation")
    void nonNumericCode_failsValidation() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("abcdef")
                .newPassword(NEW_PASSWORD)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    @DisplayName("5-digit code fails validation")
    void shortCode_failsValidation() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("12345")
                .newPassword(NEW_PASSWORD)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    @DisplayName("7-digit code fails validation")
    void longCode_failsValidation() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("1234567")
                .newPassword(NEW_PASSWORD)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    @DisplayName("Null new password fails validation")
    void nullNewPassword_failsValidation() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("123456")
                .newPassword(null)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "nocapitalletter1!",
            "NOLOWERCASE1!",
            "NoNumberHere!",
            "NoSpecialChar123"
    })
    @DisplayName("Weak new passwords fail validation")
    void weakNewPassword_failsValidation(String password) {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("123456")
                .newPassword(password)
                .build();
        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    @DisplayName("ResetPasswordRequest.toString() does not expose plaintext password")
    void toString_excludesPlaintextPassword() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .code("123456")
                .newPassword(NEW_PASSWORD)
                .build();
        String stringRepresentation = request.toString();
        assertThat(stringRepresentation).doesNotContain(NEW_PASSWORD);
    }
}
