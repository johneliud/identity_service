package io.github.johneliud.identity_service.event;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import io.github.johneliud.identity_service.model.OutboxEvent;
import io.github.johneliud.identity_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OutboxEventRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final UserEventPublisher userEventPublisher;
    private final JsonMapper objectMapper;
    private final int batchSize;

    public OutboxEventRelayService(
            OutboxEventRepository outboxEventRepository,
            UserEventPublisher userEventPublisher,
            JsonMapper objectMapper,
            @Value("${identity.outbox.relay.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.userEventPublisher = userEventPublisher;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${identity.outbox.relay.fixed-delay-ms:10000}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findByPublishedFalseOrderByCreatedAtAsc(Pageable.ofSize(batchSize));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Outbox relay: processing {} pending event(s)", pending.size());

        List<java.util.UUID> publishedIds = pending.stream()
                .filter(this::relay)
                .map(OutboxEvent::getId)
                .toList();

        if (!publishedIds.isEmpty()) {
            outboxEventRepository.markAsPublishedByIds(publishedIds);
            log.info("Outbox relay: marked {} event(s) as published", publishedIds.size());
        }

        int failed = pending.size() - publishedIds.size();
        if (failed > 0) {
            log.warn("Outbox relay: {} event(s) failed to publish and will be retried", failed);
        }
    }

    private boolean relay(OutboxEvent event) {
        try {
            EventEnvelope<?> envelope = objectMapper.readValue(
                    event.getPayload(), EventEnvelope.class);

            userEventPublisher.publish(envelope);
            return true;
        } catch (JacksonException e) {
            log.error("Outbox relay: failed to deserialise event id={} type='{}', skipping",
                    event.getId(), event.getEventType(), e);
            return false;
        } catch (Exception e) {
            log.error("Outbox relay: failed to publish event id={} type='{}', will retry",
                    event.getId(), event.getEventType(), e);
            return false;
        }
    }
}
