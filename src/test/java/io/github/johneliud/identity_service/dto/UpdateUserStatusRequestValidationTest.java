package io.github.johneliud.identity_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UpdateUserStatusRequestValidationTest {

    private final Validator validator;

    UpdateUserStatusRequestValidationTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Valid ACTIVE status passes validation")
    void validActiveStatusPassesValidation() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("ACTIVE");
        Set<ConstraintViolation<UpdateUserStatusRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Valid DEACTIVATED status passes validation")
    void validDeactivatedStatusPassesValidation() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("DEACTIVATED");
        Set<ConstraintViolation<UpdateUserStatusRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Blank status fails validation")
    void blankStatusFailsValidation() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("");
        Set<ConstraintViolation<UpdateUserStatusRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Null status fails validation")
    void nullStatusFailsValidation() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(null);
        Set<ConstraintViolation<UpdateUserStatusRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Invalid status fails validation")
    void invalidStatusFailsValidation() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("PENDING");
        Set<ConstraintViolation<UpdateUserStatusRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Status must be either ACTIVE or DEACTIVATED");
    }
}
