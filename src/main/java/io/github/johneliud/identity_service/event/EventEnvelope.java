package io.github.johneliud.identity_service.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T extends Serializable>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String source,
        T payload
) implements Serializable {

    public static <T extends Serializable> EventEnvelope<T> of(String eventType, String source, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                source,
                payload
        );
    }
}
