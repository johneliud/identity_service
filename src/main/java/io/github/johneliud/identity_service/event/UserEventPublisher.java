package io.github.johneliud.identity_service.event;

import java.io.Serializable;

public interface UserEventPublisher {
    void publish(EventEnvelope<? extends Serializable> envelope);
}
