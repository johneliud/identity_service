package io.github.johneliud.identity_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.AdminUserListResponse;
import io.github.johneliud.identity_service.dto.UpdateUserStatusRequest;
import io.github.johneliud.identity_service.dto.UserDetailResponse;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping(version = "1")
    public ResponseEntity<AdminUserListResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String email) {
        log.debug("Processing admin user list request: page={}, size={}, status={}, role={}, email={}",
                page, size, status, role, email);
        AdminUserListResponse response = adminUserService.listUsers(page, size, status, role, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{id}", version = "1")
    public ResponseEntity<UserDetailResponse> getUser(@PathVariable UUID id) {
        log.debug("Processing admin get user request for {}", id);
        UserDetailResponse response = adminUserService.getUser(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/{id}/status", version = "1")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        log.debug("Processing admin status update for user {}", id);
        UserStatus newStatus = UserStatus.valueOf(request.getStatus());
        adminUserService.updateUserStatus(id, newStatus);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
