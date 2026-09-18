package io.github.johneliud.identity_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.UpdateProfileRequest;
import io.github.johneliud.identity_service.dto.UpdateRoleRequest;
import io.github.johneliud.identity_service.dto.UserProfileResponse;
import io.github.johneliud.identity_service.service.UserProfileService;
import io.github.johneliud.identity_service.service.UserRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRoleService userRoleService;
    private final UserProfileService userProfileService;

    @GetMapping(value = "/me", version = "1")
    public ResponseEntity<UserProfileResponse> getProfile(
            @RequestHeader("X-User-Id") String userId) {
        log.debug("Processing profile request for user {}", userId);
        UserProfileResponse response = userProfileService.getProfile(UUID.fromString(userId));
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/me", version = "1")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.debug("Processing profile update for user {}", userId);
        UserProfileResponse response = userProfileService.updateProfile(
                UUID.fromString(userId), request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/{id}/roles", version = "1")
    public ResponseEntity<Void> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        log.debug("Processing role change for user {}", id);
        userRoleService.changeRole(id, request.getRoleName(), request.getAction());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
