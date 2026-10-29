package com.igot.cb.profile.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.model.WorkflowServiceHistoryEvent;
import com.igot.cb.profile.service.ServiceHistoryApprovalService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceHistoryApprovalConsumer Tests")
class ServiceHistoryApprovalConsumerTest {

    private static final String TEST_TOPIC = "workflowServiceHistoryTopic";
    private static final int TEST_PARTITION = 0;
    private static final long TEST_OFFSET = 0L;
    private static final String TEST_KEY = "test-key";

    @Mock
    private ObjectMapper mapper;

    @Mock
    private ServiceHistoryApprovalService serviceHistoryApprovalService;

    @InjectMocks
    private ServiceHistoryApprovalConsumer serviceHistoryApprovalConsumer;

    private ConsumerRecord<String, String> buildRecord(String value) {
        return new ConsumerRecord<>(TEST_TOPIC, TEST_PARTITION, TEST_OFFSET, TEST_KEY, value);
    }

    @Test
    @DisplayName("Should log and skip when record value is blank")
    void consumeServiceHistoryApprovalEvent_blankValue_skipsProcessing() throws JsonProcessingException {
        ConsumerRecord<String, String> consumerRecord = buildRecord("");

        assertDoesNotThrow(() -> serviceHistoryApprovalConsumer.consumeServiceHistoryApprovalEvent(consumerRecord));

        verify(mapper, never()).readValue(anyString(), ArgumentMatchers.<TypeReference<List<WorkflowServiceHistoryEvent>>>any());
        verify(serviceHistoryApprovalService, never()).processServiceHistoryApprovalEvent(any());
    }

    @Test
    @DisplayName("Should log and skip when record value is null")
    void consumeServiceHistoryApprovalEvent_nullValue_skipsProcessing() {
        ConsumerRecord<String, String> consumerRecord = buildRecord(null);

        assertDoesNotThrow(() -> serviceHistoryApprovalConsumer.consumeServiceHistoryApprovalEvent(consumerRecord));

        verify(serviceHistoryApprovalService, never()).processServiceHistoryApprovalEvent(any());
    }

    @Test
    @DisplayName("Should log and skip when the deserialized event list is empty")
    void consumeServiceHistoryApprovalEvent_emptyEventList_skipsProcessing() throws JsonProcessingException {
        ConsumerRecord<String, String> consumerRecord = buildRecord("[]");
        when(mapper.readValue(anyString(),
                ArgumentMatchers.<TypeReference<List<WorkflowServiceHistoryEvent>>>any())).thenReturn(List.of());

        serviceHistoryApprovalConsumer.consumeServiceHistoryApprovalEvent(consumerRecord);

        verify(serviceHistoryApprovalService, never()).processServiceHistoryApprovalEvent(any());
    }

    @Test
    @DisplayName("Should delegate the deserialized events to the approval service")
    void consumeServiceHistoryApprovalEvent_validPayload_delegatesToService() throws JsonProcessingException {
        ConsumerRecord<String, String> consumerRecord = buildRecord("[{}, {}]");
        List<WorkflowServiceHistoryEvent> events = List.of(new WorkflowServiceHistoryEvent(), new WorkflowServiceHistoryEvent());
        when(mapper.readValue(anyString(),
                ArgumentMatchers.<TypeReference<List<WorkflowServiceHistoryEvent>>>any())).thenReturn(events);

        serviceHistoryApprovalConsumer.consumeServiceHistoryApprovalEvent(consumerRecord);

        verify(serviceHistoryApprovalService).processServiceHistoryApprovalEvent(events);
    }

    @Test
    @DisplayName("Should not propagate an exception when the payload is malformed JSON")
    void consumeServiceHistoryApprovalEvent_malformedJson_doesNotThrow() throws JsonProcessingException {
        ConsumerRecord<String, String> consumerRecord = buildRecord("not-valid-json");
        when(mapper.readValue(anyString(),
                ArgumentMatchers.<TypeReference<List<WorkflowServiceHistoryEvent>>>any()))
                .thenThrow(new TestJsonProcessingException("bad json"));

        assertDoesNotThrow(() -> serviceHistoryApprovalConsumer.consumeServiceHistoryApprovalEvent(consumerRecord));

        verify(serviceHistoryApprovalService, never()).processServiceHistoryApprovalEvent(any());
    }

    @Test
    @DisplayName("Should not propagate an exception when the approval service throws")
    void consumeServiceHistoryApprovalEvent_serviceThrows_doesNotThrow() throws JsonProcessingException {
        ConsumerRecord<String, String> consumerRecord = buildRecord("[{}, {}]");
        List<WorkflowServiceHistoryEvent> events = List.of(new WorkflowServiceHistoryEvent(), new WorkflowServiceHistoryEvent());
        when(mapper.readValue(anyString(),
                ArgumentMatchers.<TypeReference<List<WorkflowServiceHistoryEvent>>>any())).thenReturn(events);
        when(serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events))
                .thenThrow(new IllegalStateException("downstream failure"));

        assertDoesNotThrow(() -> serviceHistoryApprovalConsumer.consumeServiceHistoryApprovalEvent(consumerRecord));
    }

    private static final class TestJsonProcessingException extends JsonProcessingException {
        TestJsonProcessingException(String message) {
            super(message);
        }
    }
}
