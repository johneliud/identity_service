package io.github.johneliud.identity_service.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserUpdatedEvent(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String status,
        Set<String> roles,
        String changeType,
        Instant occurredAt
) implements Serializable {}
