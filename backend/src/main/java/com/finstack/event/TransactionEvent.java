package com.finstack.event;
import com.finstack.transaction.*; import java.math.BigDecimal; import java.time.OffsetDateTime; import java.util.UUID;
public record TransactionEvent(UUID eventId,TransactionEventType eventType,OffsetDateTime occurredAt,UUID transactionId,UUID userId,UUID accountId,UUID categoryId,TransactionType transactionType,BigDecimal amount,String merchant,OffsetDateTime transactionTime,TransactionStatus status) { }
