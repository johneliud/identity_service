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
public class UpdateUserStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(ACTIVE|DEACTIVATED)$",
            message = "Status must be either ACTIVE or DEACTIVATED")
    private String status;
}
