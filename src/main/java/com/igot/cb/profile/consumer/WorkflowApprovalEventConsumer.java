package com.igot.cb.profile.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ServiceHistoryService;
import com.igot.cb.util.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for workflow-approval events published by sunbird-cb-workflow on the single shared
 * topic. Each event carries an {@code approvalType} (e.g. "DESIGNATION" today, "ORGANIZATION"
 * planned) that is dispatched to the matching private handle*Approval method. Adding support for
 * a new approvalType only requires adding a new case + private method here.
 * <p>
 * Message shape: {"eventId":"...","userId":"...","wfId":"...","approvalType":"...", ...}.
 * An unsupported approvalType is logged and skipped.
 */
@Component
public class WorkflowApprovalEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowApprovalEventConsumer.class);

    private final ObjectMapper mapper;
    private final ServiceHistoryService serviceHistoryService;

    public WorkflowApprovalEventConsumer(ObjectMapper mapper, ServiceHistoryService serviceHistoryService) {
        this.mapper = mapper;
        this.serviceHistoryService = serviceHistoryService;
    }

    /**
     * Kafka entry point for the shared workflow-approval topic. Parses the raw record into a
     * generic String map and hands it off to {@link #processEvent(Map)}. Never throws: a blank
     * or unparseable message is logged and dropped rather than crashing the listener thread.
     *
     * @param consumerRecord the raw Kafka record, value is the JSON event payload
     */
    @KafkaListener(topics = "${spring.kafka.workflow.approval.topic.name}", groupId = "${spring.kafka.workflow.approval.consumer.group.id}")
    public void consumeWorkflowApprovalEvent(ConsumerRecord<String, String> consumerRecord) {
        String message = consumerRecord.value();
        if (StringUtils.isBlank(message)) {
            logger.warn("WorkflowApprovalEventConsumer: Received empty message from topic: {}", consumerRecord.topic());
            return;
        }
        try {
            Map<String, String> event = mapper.readValue(message, new TypeReference<Map<String, String>>() {
            });
            processEvent(event);
        } catch (JsonProcessingException e) {
            logger.error("WorkflowApprovalEventConsumer: Failed to parse message: {}", message, e);
        } catch (Exception e) {
            logger.error("WorkflowApprovalEventConsumer: Unexpected error processing message: {}", message, e);
        }
    }

    /**
     * Validates the event and dispatches it to the handler for its approvalType. Missing userId
     * or an approvalType this consumer doesn't yet support (e.g. "ORGANIZATION") is logged and
     * skipped rather than raising an error, keeping the door open for new types to be added with
     * a new branch here.
     *
     * @param event deserialized event fields, all String-valued
     */
    private void processEvent(Map<String, String> event) {
        String userId = event.get(Constants.USER_ID);
        String wfId = event.get(Constants.WF_IF);
        String approvalType = event.get(Constants.APPROVAL_TYPE);

        if (StringUtils.isBlank(userId)) {
            logger.warn("WorkflowApprovalEventConsumer: Invalid event, missing userId: {}", event);
            return;
        }

        if (Constants.APPROVAL_TYPE_DESIGNATION.equalsIgnoreCase(approvalType)) {
            handleDesignationApproval(event, userId, wfId);
        } else {
            logger.info("WorkflowApprovalEventConsumer: Skipping unsupported approvalType '{}' for userId={}, wfId={}",
                    approvalType, userId, wfId);
        }
    }

    /**
     * Handles approvalType=DESIGNATION by appending a serviceHistory entry for the user.
     *
     * @param event  deserialized event fields, used here for designation/orgName
     * @param userId the user whose serviceHistory is updated
     * @param wfId   the workflow request id, used downstream for dedup/idempotency
     */
    private void handleDesignationApproval(Map<String, String> event, String userId, String wfId) {
        String designation = event.get(Constants.DESIGNATION);
        String orgName = event.get(Constants.ORG_NAME);
        boolean updated = serviceHistoryService.appendDesignationApprovalToServiceHistory(userId, wfId, designation,
                orgName);
        logResult(userId, wfId, updated);
    }

    /**
     * Logs the outcome of a designation-approval handling attempt at the appropriate level.
     *
     * @param userId  the user the entry was appended for
     * @param wfId    the workflow request id from the event
     * @param updated true if the serviceHistory append succeeded
     */
    private void logResult(String userId, String wfId, boolean updated) {
        if (updated) {
            logger.info("WorkflowApprovalEventConsumer: {} approval applied - userId={}, wfId={}",
                    Constants.APPROVAL_TYPE_DESIGNATION, userId, wfId);
        } else {
            logger.error("WorkflowApprovalEventConsumer: {} approval failed - userId={}, wfId={}",
                    Constants.APPROVAL_TYPE_DESIGNATION, userId, wfId);
        }
    }
}
