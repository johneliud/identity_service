package io.github.johneliud.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Pattern(regexp = "^(ADMIN|TRAVEL_MANAGER|TRAVELER)$",
            message = "Role must be one of: ADMIN, TRAVEL_MANAGER, TRAVELER")
    private String roleName;

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(ADD|REMOVE)$",
            message = "Action must be either ADD or REMOVE")
    private String action;
}
