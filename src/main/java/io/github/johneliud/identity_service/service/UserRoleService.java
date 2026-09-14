package io.github.johneliud.identity_service.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.RoleAlreadyAssignedException;
import io.github.johneliud.identity_service.exception.RoleNotAssignedException;
import io.github.johneliud.identity_service.exception.RoleNotFoundException;
import io.github.johneliud.identity_service.exception.UserNotFoundException;
import io.github.johneliud.identity_service.model.Role;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.repository.RoleRepository;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void changeRole(UUID userId, String roleName, String action) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleName));

        Set<Role> currentRoles = user.getRoles();

        if ("ADD".equals(action)) {
            if (currentRoles.contains(role)) {
                throw new RoleAlreadyAssignedException(
                        "User already has role: " + roleName);
            }
            currentRoles.add(role);
            log.debug("Added role {} to user {}", roleName, userId);
        } else {
            if (!currentRoles.contains(role)) {
                throw new RoleNotAssignedException(
                        "User does not have role: " + roleName);
            }
            currentRoles.remove(role);
            log.debug("Removed role {} from user {}", roleName, userId);
        }

        userRepository.save(user);
        outboxEventPublisher.publishUserUpdated(user, "ROLE_CHANGED");
    }
}
