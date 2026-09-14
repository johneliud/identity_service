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
    public void publish(EventEnvelope<? extends Serializable> envelope) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.info("Kafka publishing disabled or KafkaTemplate unavailable. Event recorded: eventType={} eventId={}",
                    envelope.eventType(), envelope.eventId());
            return;
        }

        try {
            String key = envelope.eventId().toString();
            log.info("Publishing event {} (id={}) to topic: {}", envelope.eventType(), key, topic);
            kafkaTemplate.send(topic, key, envelope);
        } catch (Exception ex) {
            log.error("Failed to publish event {} (id={}) to Kafka",
                    envelope.eventType(), envelope.eventId(), ex);
        }
    }
}
