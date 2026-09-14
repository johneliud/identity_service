package io.github.johneliud.identity_service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

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

    private static final UUID TEST_USER_ID = UUID.randomUUID();

    private EventEnvelope<UserRegisteredEvent> createTestEnvelope() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                TEST_USER_ID,
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
    @DisplayName("Returns true and sends to Kafka when broker acknowledges")
    void publish_whenEnabled_returnsTrue() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        when(kafkaTemplate.send(eq("identity.events"), eq(TEST_USER_ID.toString()), eq(envelope)))
                .thenReturn(CompletableFuture.completedFuture(null));

        boolean result = publisher.publish(envelope);

        assertThat(result).isTrue();
        verify(kafkaTemplate).send(eq("identity.events"), eq(TEST_USER_ID.toString()), eq(envelope));
    }

    @Test
    @DisplayName("Returns false when Kafka is disabled")
    void publish_whenDisabled_returnsFalse() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", false
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        boolean result = publisher.publish(envelope);

        assertThat(result).isFalse();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Returns false when KafkaTemplate is null")
    void publish_whenTemplateNull_returnsFalse() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                null, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        boolean result = publisher.publish(envelope);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Returns false when broker send throws")
    void publish_whenKafkaFails_returnsFalse() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        doThrow(new RuntimeException("Kafka broker unreachable"))
                .when(kafkaTemplate).send(any(), any(), any());

        boolean result = publisher.publish(envelope);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Returns false when broker future completes exceptionally")
    void publish_whenBrokerRejects_returnsFalse() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Topic not found"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        boolean result = publisher.publish(envelope);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Uses userId from payload as partition key")
    void publish_usesUserIdAsKey() {
        KafkaUserEventPublisher publisher = new KafkaUserEventPublisher(
                kafkaTemplate, "identity.events", true
        );
        EventEnvelope<UserRegisteredEvent> envelope = createTestEnvelope();

        when(kafkaTemplate.send(eq("identity.events"), eq(TEST_USER_ID.toString()), eq(envelope)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(envelope);

        verify(kafkaTemplate).send(eq("identity.events"), eq(TEST_USER_ID.toString()), eq(envelope));
    }
}
