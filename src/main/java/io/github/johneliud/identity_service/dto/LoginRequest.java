package io.github.johneliud.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format. Allowed format: yourname@domain.com")
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    @NotBlank(message = "Password is required")
    @ToString.Exclude
    private String password;

    @Builder
    public LoginRequest(String email, String password) {
        this.email = email == null ? null : email.trim();
        this.password = password;
    }
}
