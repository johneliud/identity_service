package io.github.johneliud.identity_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.johneliud.identity_service.config.JwtTokenProvider;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.repository.RefreshTokenRepository;
import io.github.johneliud.identity_service.repository.ResetTokenRepository;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final boolean requireEmailVerification;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            OutboxEventPublisher outboxEventPublisher,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            ResetTokenRepository resetTokenRepository,
            @Value("${identity.registration.require-email-verification:true}") boolean requireEmailVerification) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventPublisher = outboxEventPublisher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.requireEmailVerification = requireEmailVerification;
    }
}
