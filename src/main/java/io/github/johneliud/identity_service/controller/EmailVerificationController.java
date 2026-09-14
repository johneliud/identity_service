package io.github.johneliud.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.johneliud.identity_service.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping(value = "/verify-email", version = "1")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        log.debug("Processing email verification request");
        emailVerificationService.verify(token);

        return ResponseEntity
                .status(org.springframework.http.HttpStatus.OK)
                .build();
    }
}
