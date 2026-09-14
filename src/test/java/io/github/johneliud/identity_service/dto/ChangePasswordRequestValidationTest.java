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

class ChangePasswordRequestValidationTest {

    private static Validator validator;
    private static final String OLD_PASSWORD = UUID.randomUUID() + "Aa1!";
    private static final String NEW_PASSWORD = UUID.randomUUID() + "Bb2!";

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private ChangePasswordRequest createValidRequest() {
        return ChangePasswordRequest.builder()
                .currentPassword(OLD_PASSWORD)
                .newPassword(NEW_PASSWORD)
                .build();
    }

    @Test
    @DisplayName("Valid change password request passes bean validation")
    void validRequest_passesValidation() {
        ChangePasswordRequest request = createValidRequest();
        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null current password fails validation")
    void nullCurrentPassword_failsValidation() {
        ChangePasswordRequest request = createValidRequest();
        request.setCurrentPassword(null);
        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currentPassword"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Blank current password fails validation")
    void blankCurrentPassword_failsValidation(String password) {
        ChangePasswordRequest request = createValidRequest();
        request.setCurrentPassword(password);
        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currentPassword"));
    }

    @Test
    @DisplayName("Null new password fails validation")
    void nullNewPassword_failsValidation() {
        ChangePasswordRequest request = createValidRequest();
        request.setNewPassword(null);
        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
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
        ChangePasswordRequest request = createValidRequest();
        request.setNewPassword(password);
        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    @DisplayName("ChangePasswordRequest.toString() does not expose passwords")
    void toString_excludesPlaintextPasswords() {
        ChangePasswordRequest request = createValidRequest();
        String stringRepresentation = request.toString();
        assertThat(stringRepresentation).doesNotContain(OLD_PASSWORD);
        assertThat(stringRepresentation).doesNotContain(NEW_PASSWORD);
    }
}
