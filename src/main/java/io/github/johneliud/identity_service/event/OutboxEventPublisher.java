package io.github.johneliud.identity_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import io.github.johneliud.identity_service.model.OutboxEvent;
import io.github.johneliud.identity_service.model.User;
import io.github.johneliud.identity_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private static final String SOURCE = "identity-service";
    private static final String USER_REGISTERED_EVENT = "UserRegisteredEvent";
    private static final String USER_UPDATED_EVENT = "UserUpdatedEvent";

    private final OutboxEventRepository outboxEventRepository;
    private final UserEventPublisher userEventPublisher;
    private final JsonMapper objectMapper;

    public void publishUserRegistered(UserRegisteredEvent event) {
        EventEnvelope<UserRegisteredEvent> envelope = EventEnvelope.of(
                USER_REGISTERED_EVENT, SOURCE, event);

        saveAndPublish(envelope, event.userId().toString());
    }

    public void publishUserUpdated(UserUpdatedEvent event) {
        EventEnvelope<UserUpdatedEvent> envelope = EventEnvelope.of(
                USER_UPDATED_EVENT, SOURCE, event);

        saveAndPublish(envelope, event.userId().toString());
    }

    public void publishUserUpdated(User user, String changeType) {
        Set<String> roleNames = user.getRoles().stream()
                .map(io.github.johneliud.identity_service.model.Role::getName)
                .collect(Collectors.toSet());

        UserUpdatedEvent event = new UserUpdatedEvent(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                roleNames,
                changeType,
                Instant.now()
        );

        publishUserUpdated(event);
    }

    private void saveAndPublish(EventEnvelope<?> envelope, String logKey) {
        String payload = serialise(envelope);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(envelope.eventType())
                .payload(payload)
                .build();

        outboxEventRepository.save(outboxEvent);
        log.debug("Outbox record saved for {} id={}", envelope.eventType(), logKey);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Transaction committed — publishing {} id={}", envelope.eventType(), logKey);
                userEventPublisher.publish(envelope);
            }
        });
    }

    private String serialise(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "Failed to serialise event " + envelope.eventType() + " id=" + envelope.eventId(), e);
        }
    }
}
