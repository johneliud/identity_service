package io.github.johneliud.identity_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.ChangePasswordRequest;
import io.github.johneliud.identity_service.dto.ForgotPasswordRequest;
import io.github.johneliud.identity_service.dto.ResetPasswordRequest;
import io.github.johneliud.identity_service.service.PasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping(value = "/change-password", version = "1")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.debug("Processing change password request");
        passwordService.changePassword(request, userId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @PostMapping(value = "/forgot-password", version = "1")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.debug("Processing forgot password request");
        passwordService.forgotPassword(request);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @PostMapping(value = "/reset-password", version = "1")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.debug("Processing reset password request");
        passwordService.resetPassword(request);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
