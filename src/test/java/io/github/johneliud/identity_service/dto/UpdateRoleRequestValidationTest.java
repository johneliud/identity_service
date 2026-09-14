package io.github.johneliud.identity_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UpdateRoleRequestValidationTest {

    private final Validator validator;

    UpdateRoleRequestValidationTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Valid request passes validation")
    void validRequestPassesValidation() {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "ADD");
        Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Blank role name fails validation")
    void blankRoleNameFailsValidation() {
        UpdateRoleRequest request = new UpdateRoleRequest("", "ADD");
        Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("roleName"));
    }

    @Test
    @DisplayName("Null role name fails validation")
    void nullRoleNameFailsValidation() {
        UpdateRoleRequest request = new UpdateRoleRequest(null, "ADD");
        Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("Invalid role name fails validation")
    void invalidRoleNameFailsValidation() {
        UpdateRoleRequest request = new UpdateRoleRequest("SUPER_ADMIN", "ADD");
        Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Role must be one of");
    }

    @Test
    @DisplayName("Blank action fails validation")
    void blankActionFailsValidation() {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "");
        Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("action"));
    }

    @Test
    @DisplayName("Invalid action fails validation")
    void invalidActionFailsValidation() {
        UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", "DELETE");
        Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Action must be either ADD or REMOVE");
    }

    @Test
    @DisplayName("All valid roles pass validation")
    void allValidRolesPassValidation() {
        for (String role : new String[]{"ADMIN", "TRAVEL_MANAGER", "TRAVELER"}) {
            UpdateRoleRequest request = new UpdateRoleRequest(role, "ADD");
            Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("All valid actions pass validation")
    void allValidActionsPassValidation() {
        for (String action : new String[]{"ADD", "REMOVE"}) {
            UpdateRoleRequest request = new UpdateRoleRequest("ADMIN", action);
            Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }
}
