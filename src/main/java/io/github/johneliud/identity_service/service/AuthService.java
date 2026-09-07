package io.github.johneliud.identity_service.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.dto.RegisterRequest;
import io.github.johneliud.identity_service.dto.UserResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.event.UserRegisteredEvent;
import io.github.johneliud.identity_service.exception.RoleNotFoundException;
import io.github.johneliud.identity_service.exception.UserAlreadyExistsException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthService {

    public static final String DEFAULT_ROLE = "TRAVELER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;
    private final boolean requireEmailVerification;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            OutboxEventPublisher outboxEventPublisher,
            @Value("${identity.registration.require-email-verification:true}") boolean requireEmailVerification) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventPublisher = outboxEventPublisher;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected: email '{}' already in use", normalizedEmail);
            throw new UserAlreadyExistsException("User with email '" + normalizedEmail + "' already exists");
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> {
                    log.error("Default role '{}' not found in database", DEFAULT_ROLE);
                    return new RoleNotFoundException("Default role '" + DEFAULT_ROLE + "' not found");
                });

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        UserStatus initialStatus = requireEmailVerification ? UserStatus.PENDING : UserStatus.ACTIVE;
        boolean emailVerified = !requireEmailVerification;

        String trimmedLastName = (request.getLastName() != null && !request.getLastName().isBlank())
                ? request.getLastName().trim()
                : null;

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(hashedPassword)
                .firstName(request.getFirstName().trim())
                .lastName(trimmedLastName)
                .status(initialStatus)
                .emailVerified(emailVerified)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully registered new user: id='{}', email='{}', status='{}'",
                savedUser.getId(), savedUser.getEmail(), savedUser.getStatus());

        Set<String> roleNames = savedUser.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        UserRegisteredEvent event = new UserRegisteredEvent(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getStatus().name(),
                roleNames,
                Instant.now()
        );

        outboxEventPublisher.publishUserRegistered(event);

        return UserResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .status(savedUser.getStatus())
                .emailVerified(savedUser.getEmailVerified())
                .roles(roleNames)
                .createdAt(savedUser.getCreatedAt())
                .updatedAt(savedUser.getUpdatedAt())
                .build();
    }
}
