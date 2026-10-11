package com.finstack.event;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="app.kafka") public record KafkaProperties(boolean enabled,String transactionTopic,String fraudTopic) { }
