package io.github.johneliud.identity_service.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import io.github.johneliud.identity_service.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = adminProperties.getEmail();

        if (userRepository.existsByEmail(email)) {
            log.debug("Admin user already exists: {}", email);
            return;
        }

        Role adminRole = roleRepository.findByName(RoleConstants.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role not found. Ensure V2__seed_roles.sql has run."));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User adminUser = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(adminProperties.getPassword()))
                .firstName(adminProperties.getFirstName())
                .lastName(adminProperties.getLastName())
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(roles)
                .build();

        userRepository.save(adminUser);
        log.info("Admin user seeded: {}", email);
    }
}
