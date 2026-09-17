package io.github.johneliud.identity_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Verification code must be 6 digits")
    private String code;

    @NotBlank(message = "New password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,100}$",
        message = "New password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    @Size(max = 100, message = "New password must not exceed 100 characters")
    @ToString.Exclude
    private String newPassword;

    @AssertTrue(message = "Password must not exceed 72 bytes when UTF-8 encoded")
    public boolean isPasswordWithinBcryptLimit() {
        return newPassword == null
                || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
