package io.github.johneliud.identity_service.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.github.johneliud.identity_service.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSummary {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private Boolean emailVerified;
    private Set<String> roles;
    private Instant createdAt;
}
