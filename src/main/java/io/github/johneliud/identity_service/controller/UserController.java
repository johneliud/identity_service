package io.github.johneliud.identity_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.UpdateRoleRequest;
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

    @PatchMapping(value = "/{id}/roles", version = "1")
    public ResponseEntity<Void> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        log.debug("Processing role change for user {}", id);
        userRoleService.changeRole(id, request.getRoleName(), request.getAction());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
