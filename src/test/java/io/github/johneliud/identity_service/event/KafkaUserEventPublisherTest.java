package io.github.johneliud.identity_service.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaUserEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private EventEnvelope<UserRegisteredEvent> createTestEnvelope() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                "test@example.com",
                "Test",
                "User",
                "PENDING",
                Set.of("TRAVELER"),
                Instant.now()
        );
        return EventEnvelope.of("UserRegisteredEvent", "identity-service", event);
    }

    @Test
    @DisplayName("Publishes envelope to Kafka when kafkaEnabled is true")
    void publish_whenEnabled_sendsEnvelope() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        publisher.publish(envelope);

        verify(kafkaTemplate).send(eq("identity.events"), eq(envelope.eventId().toString()), eq(envelope));
    }

    @Test
    @DisplayName("Does not publish to Kafka when kafkaEnabled is false")
    void publish_whenDisabled_doesNotSend() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", false
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        publisher.publish(envelope);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Does not fail or throw exception when KafkaTemplate is null")
    void publish_whenTemplateNull_doesNotThrow() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                null, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        publisher.publish(envelope);
    }

    @Test
    @DisplayName("Catches Kafka exceptions gracefully without crashing caller")
    void publish_whenKafkaFails_catchesException() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        doThrow(new RuntimeException("Kafka broker unreachable"))
                .when(kafkaTemplate).send(any(), any(), any());

        publisher.publish(envelope);
    }
}
