package io.github.johneliud.identity_service.controller;

import io.github.johneliud.identity_service.service.AuthService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
}
