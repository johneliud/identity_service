package io.github.johneliud.identity_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.RegisterRequest;
import io.github.johneliud.identity_service.dto.UserResponse;
import io.github.johneliud.identity_service.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class RegisterController {

    private final RegistrationService registrationService;

    @PostMapping(value = "/register", version = "1")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("Processing registration request");
        UserResponse response = registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
