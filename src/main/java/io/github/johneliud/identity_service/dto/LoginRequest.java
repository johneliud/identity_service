package io.github.johneliud.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format. Allowed format: yourname@domain.com")
    private String email;

    @NotBlank(message = "Password is required")
    @ToString.Exclude
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
