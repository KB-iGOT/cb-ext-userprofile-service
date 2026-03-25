package com.igot.cb.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an event to a specified Kafka topic with a key
     *
     * @param topic The Kafka topic name
     * @param key   The message key (used for partitioning)
     * @param event The event object to publish (will be serialized to JSON)
     *
     */
    public void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
            log.info("Successfully published event to topic: {} with key: {}",
                    topic, key);
        } catch (Exception e) {
            log.error("Failed to publish event to topic: {} with key: {}",
                    topic, key, e);
            // Don't throw exception to avoid blocking the caller's operation
        }
    }

    /**
     * Publishes an event to a specified Kafka topic without a specific key
     * The partition will be chosen using round-robin
     *
     * @param topic      The Kafka topic name
     * @param event      The event object to publish (will be serialized to JSON)
     * @param identifier An identifier for logging purposes (e.g., userId, eventId)
     */
    public void publish(String topic, Object event, String identifier) {
        try {
            log.info("Publishing event to topic: {} identifier: {}", topic, identifier);
            kafkaTemplate.send(topic, event);
            log.info("Successfully published event to topic: {} identifier: {}", topic, identifier);
        } catch (Exception e) {
            log.error("Failed to publish event to topic: {} identifier: {}", topic, identifier, e);
            // Don't throw exception to avoid blocking the caller's operation
        }
    }

    /**
     * Publishes an event to a specified Kafka topic with minimal logging
     * Use this for high-frequency events where detailed logging isn't needed
     *
     * @param topic The Kafka topic name
     * @param event The event object to publish (will be serialized to JSON)
     */
    public void publish(String topic, Object event) {
        try {
            kafkaTemplate.send(topic, event);
        } catch (Exception e) {
            log.error("Failed to publish event to topic: {}", topic, e);
        }
    }
}

