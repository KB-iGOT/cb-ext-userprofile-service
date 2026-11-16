package com.igot.cb.profile.consumer;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.model.WorkflowServiceHistoryEvent;
import com.igot.cb.profile.service.ServiceHistoryApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes the org-transfer/designation-change service-history approval events
 * published by sunbird-cb-workflow and applies them to the user's serviceHistory
 * in user_extended_profile.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ServiceHistoryApprovalConsumer {

    private final ObjectMapper mapper;

    private final ServiceHistoryApprovalService serviceHistoryApprovalService;

    @KafkaListener(topics = "${kafka.topics.workflow.service.history}",
            groupId = "${kafka.consumer.group.workflow.service.history}")
    public void consumeServiceHistoryApprovalEvent(ConsumerRecord<String, String> consumerRecord) {
        try {
            if (StringUtils.isBlank(consumerRecord.value())) {
                log.warn("Received blank service history approval event on topic: {}", consumerRecord.topic());
                return;
            }
            List<WorkflowServiceHistoryEvent> events = mapper.readValue(consumerRecord.value(),
                    new TypeReference<List<WorkflowServiceHistoryEvent>>() {
                    });
            if (CollectionUtils.isEmpty(events)) {
                log.warn("Received empty service history approval event list on topic: {}", consumerRecord.topic());
                return;
            }
            serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);
        } catch (Exception e) {
            log.error("Error processing service history approval event from topic: {}", consumerRecord.topic(), e);
        }
    }
}
