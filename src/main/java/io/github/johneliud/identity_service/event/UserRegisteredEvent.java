package io.github.johneliud.identity_service.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String status,
        Set<String> roles,
        Instant occurredAt
) implements Serializable {}
