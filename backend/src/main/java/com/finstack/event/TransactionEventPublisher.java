package com.finstack.event;

import com.finstack.fraud.FraudDetectionService;
import com.finstack.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TransactionEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaProperties properties;
    private final KafkaTemplate<String, TransactionEvent> kafka;
    private final FraudDetectionService fraudDetectionService;

    public TransactionEventPublisher(
            KafkaProperties properties,
            ObjectProvider<KafkaTemplate<String, TransactionEvent>> provider,
            FraudDetectionService fraudDetectionService
    ) {
        this.properties = properties;
        this.kafka = provider.getIfAvailable();
        this.fraudDetectionService = fraudDetectionService;
    }

    public void afterCommit(Transaction transaction, TransactionEventType type) {
        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(), type, OffsetDateTime.now(ZoneOffset.UTC), transaction.getId(),
                transaction.getAccount().getUser().getId(), transaction.getAccount().getId(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.getTransactionType(), transaction.getAmount(), transaction.getMerchant(),
                transaction.getTransactionTime(), transaction.getStatus()
        );
        Runnable dispatch = () -> dispatchAfterCommit(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void dispatchAfterCommit(TransactionEvent event) {
        if (!properties.enabled() || kafka == null) {
            if (properties.enabled()) {
                log.warn("Kafka is enabled but no template is available; using local fraud-assessment fallback for event {}", event.eventId());
            }
            assessLocally(event);
            return;
        }
        try {
            kafka.send(properties.transactionTopic(), event.transactionId().toString(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.warn("Kafka publication failed after commit for event {}; using local fraud-assessment fallback", event.eventId());
                            assessLocally(event);
                        }
                    });
        } catch (RuntimeException exception) {
            log.warn("Kafka publication failed after commit for event {}; using local fraud-assessment fallback", event.eventId());
            assessLocally(event);
        }
    }

    private void assessLocally(TransactionEvent event) {
        try {
            fraudDetectionService.assess(event);
        } catch (RuntimeException exception) {
            log.error("Local fallback fraud assessment failed for event {}", event.eventId(), exception);
        }
    }
}
