package com.nikita.ticketing.booking.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    public void relay() {
        List<OutboxEvent> unpublished = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : unpublished) {
            try {
                outboxKafkaTemplate.send(event.getAggregateType(), event.getAggregateId(), event.getPayload()).get();
                event.setPublished(true);
                outboxEventRepository.save(event);
            } catch (ExecutionException | InterruptedException e) {
                log.warn("Outbox relay failed for event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
