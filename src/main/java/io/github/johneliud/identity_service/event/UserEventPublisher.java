package io.github.johneliud.identity_service.event;

import java.io.Serializable;

public interface UserEventPublisher {

    boolean publish(EventEnvelope<? extends Serializable> envelope);
}
