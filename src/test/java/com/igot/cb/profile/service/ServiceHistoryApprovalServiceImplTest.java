package com.igot.cb.profile.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.model.WorkflowServiceHistoryEvent;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceHistoryApprovalServiceImpl Tests")
class ServiceHistoryApprovalServiceImplTest {

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_WF_ID = "wf-9001";
    private static final String TEST_EVENT_ID = "event-1";
    private static final String TEST_EVENT_TYPE = "WorkflowApprovalEventV1";
    private static final String TEST_TIMESTAMP = "2026-10-28T10:15:30.123Z";
    private static final String TEST_SOURCE = "sunbird-cb-workflow";
    private static final String OLD_DESIGNATION = "Additional Chief Mechanical Engineer";
    private static final String NEW_DESIGNATION = "Agriculture Supervisor";
    private static final String OLD_ORG_ID = "org-old";
    private static final String NEW_ORG_ID = "org-new";
    private static final String SAME_ORG_ID = "org-same";
    private static final String OLD_ORG_NAME = "Old Org";
    private static final String NEW_ORG_NAME = "New Org";
    private static final String PLACEHOLDER_JSON = "placeholder-json";
    private static final String FAILED_RESPONSE = "failed";

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private ProjectUtil projectUtil;

    @Captor
    private ArgumentCaptor<List<Map<String, Object>>> contextDataCaptor;

    @InjectMocks
    private ServiceHistoryApprovalServiceImpl serviceHistoryApprovalService;

    private WorkflowServiceHistoryEvent buildEvent(String entryType, String approvalType, String requestType,
            String designation, String orgName, String orgId) {
        return new WorkflowServiceHistoryEvent(TEST_EVENT_ID, TEST_EVENT_TYPE, TEST_TIMESTAMP, TEST_WF_ID,
                TEST_SOURCE, TEST_USER_ID, TEST_WF_ID, approvalType, requestType, entryType, designation, orgName,
                orgId);
    }

    private Map<String, Object> buildHistoryEntry(String uuid, Object index, String designation, String orgName,
            String orgId, String endDate, String currentlyWorking) {
        Map<String, Object> entry = new HashMap<>();
        entry.put(Constants.UUID, uuid);
        entry.put(Constants.INDEX, index);
        entry.put(Constants.DESIGNATION, designation);
        entry.put(Constants.ORG_NAME, orgName);
        entry.put(Constants.ORG_ID, orgId);
        entry.put(Constants.END_DATE, endDate);
        entry.put(Constants.CURRENTLY_WORKING, currentlyWorking);
        return entry;
    }

    private void stubActiveUser() {
        Map<String, Object> userRow = new HashMap<>();
        userRow.put(Constants.ID, TEST_USER_ID);
        userRow.put(Constants.ISDELETED, false);
        when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER),
                anyMap(), anyList(), eq(TEST_USER_ID))).thenReturn(List.of(userRow));
    }

    private void stubExistingHistory(List<Map<String, Object>> existingEntries) throws IOException {
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.CONTEXT_DATA, PLACEHOLDER_JSON);
        when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(row));
        when(projectUtil.parseListOfMap(PLACEHOLDER_JSON)).thenReturn(new ArrayList<>(existingEntries));
    }

    private void stubNoExistingHistory() {
        when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
    }

    private void stubSaveSuccess() {
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE), anyMap())).thenReturn(success);
    }

    private void stubSaveFailure() {
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        ApiResponse failure = new ApiResponse();
        failure.put(Constants.RESPONSE, FAILED_RESPONSE);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(failure);
    }

    private List<WorkflowServiceHistoryEvent> designationChangeEvents() {
        return List.of(
                buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                        OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                        NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));
    }

    private List<WorkflowServiceHistoryEvent> orgTransferEvents() {
        return List.of(
                buildEvent(Constants.ENTRY_TYPE_PREVIOUS, "ORGANIZATION", "ORG_TRANSFER", OLD_DESIGNATION,
                        OLD_ORG_NAME, OLD_ORG_ID),
                buildEvent(Constants.ENTRY_TYPE_CURRENT, "ORGANIZATION", "ORG_TRANSFER", OLD_DESIGNATION,
                        NEW_ORG_NAME, NEW_ORG_ID));
    }

    @Nested
    @DisplayName("Payload and guard check tests")
    class GuardCheckTests {

        @Test
        @DisplayName("Should return false when events list is null")
        void processServiceHistoryApprovalEvent_nullEvents_returnsFalse() {
            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(null);

            assertFalse(result);
            verify(cassandraOperation, never()).getRecordsByPropertiesByKey(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when events list has only one entry")
        void processServiceHistoryApprovalEvent_singleEntry_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when events list has three entries")
        void processServiceHistoryApprovalEvent_threeEntries_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when PREVIOUS entry is missing")
        void processServiceHistoryApprovalEvent_missingPrevious_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when CURRENT entry is missing")
        void processServiceHistoryApprovalEvent_missingCurrent_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false and skip Cassandra lookup when userId is blank")
        void processServiceHistoryApprovalEvent_blankUserId_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = withBlankUserId();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
            verify(cassandraOperation, never()).getRecordsByPropertiesByKey(any(), eq(Constants.USER), any(), any(),
                    any());
        }

        private List<WorkflowServiceHistoryEvent> withBlankUserId() {
            WorkflowServiceHistoryEvent previous = buildEvent(Constants.ENTRY_TYPE_PREVIOUS,
                    Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE", OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            WorkflowServiceHistoryEvent current = buildEvent(Constants.ENTRY_TYPE_CURRENT,
                    Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE", NEW_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            current.setUserId("");
            previous.setUserId("");
            return List.of(previous, current);
        }

        @Test
        @DisplayName("Should return false when user is not found in Cassandra")
        void processServiceHistoryApprovalEvent_userNotFound_returnsFalse() {
            when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER),
                    anyMap(), anyList(), eq(TEST_USER_ID))).thenReturn(new ArrayList<>());
            List<WorkflowServiceHistoryEvent> events = designationChangeEvents();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when user is deleted")
        void processServiceHistoryApprovalEvent_userDeleted_returnsFalse() {
            Map<String, Object> userRow = new HashMap<>();
            userRow.put(Constants.ID, TEST_USER_ID);
            userRow.put(Constants.ISDELETED, true);
            when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER),
                    anyMap(), anyList(), eq(TEST_USER_ID))).thenReturn(List.of(userRow));
            List<WorkflowServiceHistoryEvent> events = designationChangeEvents();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Designation-change path tests")
    class DesignationChangeTests {

        @Test
        @DisplayName("Should skip without writing when designation is unchanged")
        void processServiceHistoryApprovalEvent_designationUnchanged_returnsFalse() {
            stubActiveUser();
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when no existing service history is found")
        void processServiceHistoryApprovalEvent_noExistingHistory_returnsFalse() {
            stubActiveUser();
            stubNoExistingHistory();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when highest-index entry does not match PREVIOUS event")
        void processServiceHistoryApprovalEvent_highestEntryMismatch_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> mismatched = buildHistoryEntry("uuid-0", 0, "Some Other Designation", OLD_ORG_NAME,
                    OLD_ORG_ID, "", "true");
            stubExistingHistory(List.of(mismatched));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should close highest-index entry and append new entry on match")
        void processServiceHistoryApprovalEvent_match_closesAndAppendsEntry() throws IOException {
            stubActiveUser();
            Map<String, Object> unrelated = buildHistoryEntry("uuid-0", 0, "Junior Engineer", OLD_ORG_NAME,
                    OLD_ORG_ID, "2020-01-01T00:00:00Z", "false");
            Map<String, Object> current = buildHistoryEntry("uuid-1", 1, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID,
                    "", "true");
            stubExistingHistory(new ArrayList<>(List.of(unrelated, current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(3, saved.size());

            Map<String, Object> closedEntry = saved.get(1);
            assertEquals("false", closedEntry.get(Constants.CURRENTLY_WORKING));
            assertTrue(StringUtils.isNotBlank((String) closedEntry.get(Constants.END_DATE)));

            Map<String, Object> newEntry = saved.get(2);
            assertEquals(2, newEntry.get(Constants.INDEX));
            assertEquals(NEW_DESIGNATION, newEntry.get(Constants.DESIGNATION));
            assertEquals(SAME_ORG_ID, newEntry.get(Constants.ORG_ID));
            assertEquals("true", newEntry.get(Constants.CURRENTLY_WORKING));
            assertEquals("true", newEntry.get(Constants.APPROVED_VIA_WORKFLOW));
            assertNotNull(newEntry.get(Constants.UUID));
        }

        @Test
        @DisplayName("Should resolve numeric-string index values and increment correctly")
        void processServiceHistoryApprovalEvent_stringIndex_resolvesAndIncrements() throws IOException {
            stubActiveUser();
            Map<String, Object> lowerIndex = buildHistoryEntry("uuid-0", "0", "Junior Engineer", OLD_ORG_NAME,
                    OLD_ORG_ID, "2020-01-01T00:00:00Z", "false");
            Map<String, Object> current = buildHistoryEntry("uuid-1", "3", OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", "true");
            stubExistingHistory(new ArrayList<>(List.of(lowerIndex, current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            Map<String, Object> newEntry = contextDataCaptor.getValue().get(2);
            assertEquals(4, newEntry.get(Constants.INDEX));
        }

        @Test
        @DisplayName("Should default a missing index to -1 and start the new index at 0")
        void processServiceHistoryApprovalEvent_missingIndex_defaultsToZero() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry("uuid-0", null, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", "true");
            current.remove(Constants.INDEX);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            Map<String, Object> newEntry = contextDataCaptor.getValue().get(1);
            assertEquals(0, newEntry.get(Constants.INDEX));
        }

        @Test
        @DisplayName("Should return false when Cassandra insert fails")
        void processServiceHistoryApprovalEvent_saveFails_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry("uuid-0", 0, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID,
                    "", "true");
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveFailure();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Org-transfer path tests")
    class OrgTransferTests {

        @Test
        @DisplayName("Should skip without writing when orgId is unchanged")
        void processServiceHistoryApprovalEvent_orgIdUnchanged_returnsFalse() {
            stubActiveUser();
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, "ORGANIZATION", "ORG_TRANSFER", OLD_DESIGNATION,
                            OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, "ORGANIZATION", "ORG_TRANSFER", OLD_DESIGNATION,
                            OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when highest-index entry's orgId does not match PREVIOUS")
        void processServiceHistoryApprovalEvent_highestEntryMismatch_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> mismatched = buildHistoryEntry("uuid-0", 0, OLD_DESIGNATION, "Some Other Org",
                    "some-other-org-id", "", "true");
            stubExistingHistory(new ArrayList<>(List.of(mismatched)));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should close highest-index entry and append new entry, carrying designation unchanged")
        void processServiceHistoryApprovalEvent_match_closesAndAppendsEntry() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry("uuid-0", 0, OLD_DESIGNATION, OLD_ORG_NAME, OLD_ORG_ID,
                    "", "true");
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());

            Map<String, Object> closedEntry = saved.get(0);
            assertEquals("false", closedEntry.get(Constants.CURRENTLY_WORKING));
            assertTrue(StringUtils.isNotBlank((String) closedEntry.get(Constants.END_DATE)));

            Map<String, Object> newEntry = saved.get(1);
            assertEquals(1, newEntry.get(Constants.INDEX));
            assertEquals(OLD_DESIGNATION, newEntry.get(Constants.DESIGNATION));
            assertEquals(NEW_ORG_ID, newEntry.get(Constants.ORG_ID));
            assertEquals(NEW_ORG_NAME, newEntry.get(Constants.ORG_NAME));
            assertEquals("true", newEntry.get(Constants.CURRENTLY_WORKING));
            assertEquals("true", newEntry.get(Constants.APPROVED_VIA_WORKFLOW));
        }

        @Test
        @DisplayName("Should return false when no existing service history is found")
        void processServiceHistoryApprovalEvent_noExistingHistory_returnsFalse() {
            stubActiveUser();
            stubNoExistingHistory();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when Cassandra insert fails")
        void processServiceHistoryApprovalEvent_saveFails_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry("uuid-0", 0, OLD_DESIGNATION, OLD_ORG_NAME, OLD_ORG_ID,
                    "", "true");
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveFailure();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Malformed stored data tests")
    class MalformedDataTests {

        @Test
        @DisplayName("Should treat unparseable contextData as empty history and return false")
        void processServiceHistoryApprovalEvent_unparseableContextData_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> row = new HashMap<>();
            row.put(Constants.CONTEXT_DATA, PLACEHOLDER_JSON);
            when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD),
                    eq(Constants.TABLE_USER_EXTENDED_PROFILE), anyMap(), isNull(), isNull()))
                    .thenReturn(List.of(row));
            when(projectUtil.parseListOfMap(PLACEHOLDER_JSON)).thenThrow(new IOException("bad json"));

            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION, "DESIGNATION_CHANGE",
                            NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }
    }
}
