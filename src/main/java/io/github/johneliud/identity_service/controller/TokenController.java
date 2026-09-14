package io.github.johneliud.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.dto.RefreshTokenRequest;
import io.github.johneliud.identity_service.dto.RefreshTokenResponse;
import io.github.johneliud.identity_service.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class TokenController {

    private final TokenService tokenService;

    @PostMapping(value = "/refresh", version = "1")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Processing token refresh request");
        RefreshTokenResponse response = tokenService.refresh(request);

        return ResponseEntity
                .status(org.springframework.http.HttpStatus.OK)
                .body(response);
    }

    @PostMapping(value = "/logout", version = "1")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Processing logout request");
        tokenService.logout(request);

        return ResponseEntity
                .status(org.springframework.http.HttpStatus.NO_CONTENT)
                .build();
    }
}
