package io.github.johneliud.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.LoginRequest;
import io.github.johneliud.identity_service.dto.LoginResponse;
import io.github.johneliud.identity_service.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final LoginService loginService;

    @PostMapping(value = "/login", version = "1")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("Processing login request");
        LoginResponse response = loginService.login(request);

        return ResponseEntity
                .status(org.springframework.http.HttpStatus.OK)
                .body(response);
    }
}
