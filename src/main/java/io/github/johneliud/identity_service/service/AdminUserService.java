package io.github.johneliud.identity_service.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.johneliud.identity_service.dto.AdminUserListResponse;
import io.github.johneliud.identity_service.dto.AdminUserSummary;
import io.github.johneliud.identity_service.dto.UserDetailResponse;
import io.github.johneliud.identity_service.event.OutboxEventPublisher;
import io.github.johneliud.identity_service.exception.UserNotFoundException;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.model.UserStatus;
import io.github.johneliud.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminUserListResponse listUsers(int page, int size, String status,
                                           String roleName, String email) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findAllFiltered(status, email, roleName, pageable);

        java.util.List<AdminUserSummary> summaries = userPage.getContent().stream()
                .map(this::toSummary)
                .toList();

        return AdminUserListResponse.builder()
                .content(summaries)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserDetailResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        return toDetail(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void updateUserStatus(UUID userId, UserStatus newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(newStatus);
        userRepository.save(user);

        String changeType = switch (newStatus) {
            case DEACTIVATED -> "USER_DEACTIVATED";
            case ACTIVE -> "USER_REACTIVATED";
            default -> "STATUS_CHANGED";
        };
        outboxEventPublisher.publishUserUpdated(user, changeType);
        log.debug("User {} status changed from {} to {}", userId, oldStatus, newStatus);
    }

    private AdminUserSummary toSummary(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(io.github.johneliud.identity_service.model.Role::getName)
                .collect(Collectors.toSet());

        return AdminUserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserDetailResponse toDetail(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(io.github.johneliud.identity_service.model.Role::getName)
                .collect(Collectors.toSet());

        return UserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
