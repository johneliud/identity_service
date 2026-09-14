package io.github.johneliud.identity_service.event;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class KafkaUserEventPublisher implements UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final boolean kafkaEnabled;

    public KafkaUserEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${identity.kafka.topics.identity-events:identity.events}") String topic,
            @Value("${identity.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Override
    public boolean publish(EventEnvelope<? extends Serializable> envelope) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.info("Kafka publishing disabled or KafkaTemplate unavailable. Event recorded: eventType={} eventId={}",
                    envelope.eventType(), envelope.eventId());
            return false;
        }

        try {
            String key = extractUserId(envelope);
            log.info("Publishing event {} (id={}, userId={}) to topic: {}",
                    envelope.eventType(), envelope.eventId(), key, topic);
            kafkaTemplate.send(topic, key, envelope).join();
            return true;
        } catch (Exception ex) {
            log.error("Failed to publish event {} (id={}) to Kafka",
                    envelope.eventType(), envelope.eventId(), ex);
            return false;
        }
    }

    private String extractUserId(EventEnvelope<? extends Serializable> envelope) {
        try {
            var payload = envelope.payload();
            var method = payload.getClass().getMethod("userId");
            Object userId = method.invoke(payload);
            return userId != null ? userId.toString() : envelope.eventId().toString();
        } catch (Exception e) {
            log.warn("Could not extract userId from payload, falling back to eventId");
            return envelope.eventId().toString();
        }
    }
}
