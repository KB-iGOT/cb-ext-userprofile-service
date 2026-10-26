package com.igot.cb.profile.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ServiceHistoryService;
import com.igot.cb.util.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowApprovalEventConsumerTest {

    @Mock
    private ServiceHistoryService serviceHistoryService;

    private WorkflowApprovalEventConsumer consumer;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String TOPIC = "workflowServiceHistoryTopic";
    private static final String USER_ID = "user-123";
    private static final String WF_ID = "wf-456";
    private static final String DESIGNATION = "Additional Chief Mechanical Engineer";
    private static final String ORG_NAME = "Health and Family Welfare Department Karnataka";

    @BeforeEach
    void setUp() {
        consumer = new WorkflowApprovalEventConsumer(mapper, serviceHistoryService);
    }

    private ConsumerRecord<String, String> recordWithValue(String value) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, USER_ID, value);
    }

    private String designationApprovalEventJson(String userId, String wfId, String approvalType,
            String designation, String orgName) throws Exception {
        Map<String, String> event = new HashMap<>();
        event.put(Constants.USER_ID, userId);
        event.put(Constants.WF_IF, wfId);
        event.put(Constants.APPROVAL_TYPE, approvalType);
        event.put(Constants.DESIGNATION, designation);
        event.put(Constants.ORG_NAME, orgName);
        return mapper.writeValueAsString(event);
    }

    @Test
    void consumeWorkflowApprovalEvent_blankMessage_skipsProcessing() {
        assertDoesNotThrow(() -> consumer.consumeWorkflowApprovalEvent(recordWithValue("")));

        verify(serviceHistoryService, never())
                .appendDesignationApprovalToServiceHistory(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeWorkflowApprovalEvent_invalidJson_logsAndDoesNotThrow() {
        assertDoesNotThrow(() -> consumer.consumeWorkflowApprovalEvent(recordWithValue("{not-valid-json")));

        verify(serviceHistoryService, never())
                .appendDesignationApprovalToServiceHistory(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeWorkflowApprovalEvent_missingUserId_skipsProcessing() throws Exception {
        String json = designationApprovalEventJson(null, WF_ID, Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION,
                ORG_NAME);

        consumer.consumeWorkflowApprovalEvent(recordWithValue(json));

        verify(serviceHistoryService, never())
                .appendDesignationApprovalToServiceHistory(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeWorkflowApprovalEvent_unsupportedApprovalType_skipsProcessing() throws Exception {
        String json = designationApprovalEventJson(USER_ID, WF_ID, "ORGANIZATION", DESIGNATION, ORG_NAME);

        consumer.consumeWorkflowApprovalEvent(recordWithValue(json));

        verify(serviceHistoryService, never())
                .appendDesignationApprovalToServiceHistory(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeWorkflowApprovalEvent_designationApproval_delegatesToServiceHistoryService() throws Exception {
        String json = designationApprovalEventJson(USER_ID, WF_ID, Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION,
                ORG_NAME);
        when(serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION, ORG_NAME))
                .thenReturn(true);

        consumer.consumeWorkflowApprovalEvent(recordWithValue(json));

        verify(serviceHistoryService).appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);
    }

    @Test
    void consumeWorkflowApprovalEvent_designationApprovalTypeCaseInsensitive_delegatesToServiceHistoryService()
            throws Exception {
        String json = designationApprovalEventJson(USER_ID, WF_ID, "designation", DESIGNATION, ORG_NAME);
        when(serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION, ORG_NAME))
                .thenReturn(true);

        consumer.consumeWorkflowApprovalEvent(recordWithValue(json));

        verify(serviceHistoryService).appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);
    }

    @Test
    void consumeWorkflowApprovalEvent_serviceHistoryUpdateFails_doesNotThrow() throws Exception {
        String json = designationApprovalEventJson(USER_ID, WF_ID, Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION,
                ORG_NAME);
        when(serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION, ORG_NAME))
                .thenReturn(false);

        assertDoesNotThrow(() -> consumer.consumeWorkflowApprovalEvent(recordWithValue(json)));
    }

    @Test
    void consumeWorkflowApprovalEvent_serviceHistoryThrowsUnexpectedException_doesNotPropagate() throws Exception {
        String json = designationApprovalEventJson(USER_ID, WF_ID, Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION,
                ORG_NAME);
        when(serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION, ORG_NAME))
                .thenThrow(new RuntimeException("unexpected failure"));

        assertDoesNotThrow(() -> consumer.consumeWorkflowApprovalEvent(recordWithValue(json)));
    }
}
