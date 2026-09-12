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

class RegisterRequestValidationTest {

    private static Validator validator;
    private static final String USER_PASSWORD = UUID.randomUUID() + "Aa1!";

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private RegisterRequest createValidRequest() {
        return RegisterRequest.builder()
                .email("traveler@example.com")
                .password(USER_PASSWORD)
                .firstName("John")
                .lastName("Doe")
                .build();
    }

    @Test
    @DisplayName("Valid registration request passes bean validation")
    void validRequest_passesValidation() {
        RegisterRequest request = createValidRequest();
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Valid registration request with null last name passes bean validation")
    void validRequest_withNullLastName_passesValidation() {
        RegisterRequest request = createValidRequest();
        request.setLastName(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email", "missing-at-sign.com", "@missingusername.com"})
    @DisplayName("Invalid email formats fail validation")
    void invalidEmail_failsValidation(String email) {
        RegisterRequest request = createValidRequest();
        request.setEmail(email);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("Null email fails validation")
    void nullEmail_failsValidation() {
        RegisterRequest request = createValidRequest();
        request.setEmail(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "nocapitalletter1!",
            "NOLOWERCASE1!",
            "NoNumberHere!",
            "NoSpecialChar123",
            "        "
    })
    @DisplayName("Weak passwords fail password strength validation")
    void weakPassword_failsValidation(String password) {
        RegisterRequest request = createValidRequest();
        request.setPassword(password);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("Null password fails validation")
    void nullPassword_failsValidation() {
        RegisterRequest request = createValidRequest();
        request.setPassword(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Blank first name fails validation")
    void blankFirstName_failsValidation(String firstName) {
        RegisterRequest request = createValidRequest();
        request.setFirstName(firstName);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));
    }

    @Test
    @DisplayName("Null first name fails validation")
    void nullFirstName_failsValidation() {
        RegisterRequest request = createValidRequest();
        request.setFirstName(null);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));
    }

    @Test
    @DisplayName("RegisterRequest.toString() does not expose plaintext password")
    void toString_excludesPlaintextPassword() {
        RegisterRequest request = createValidRequest();
        String stringRepresentation = request.toString();
        assertThat(stringRepresentation).doesNotContain(USER_PASSWORD);
    }
}
