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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.model.WorkflowServiceHistoryEvent;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
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
    private static final String DESIGNATION_CHANGE_REQUEST = "DESIGNATION_CHANGE";
    private static final String ORG_TRANSFER_REQUEST = "ORG_TRANSFER";
    private static final String APPROVAL_TYPE_ORG = "ORGANIZATION";
    private static final String OTHER_DESIGNATION = "Some Other Designation";
    private static final String OTHER_ORG_NAME = "Some Other Org";
    private static final String OTHER_ORG_ID = "some-other-org-id";
    private static final String UUID_ZERO = "uuid-0";
    private static final String UUID_ONE = "uuid-1";
    private static final String EMPTY_JSON = "{}";
    private static final String BAD_JSON_MESSAGE = "bad json";
    private static final String UNPARSEABLE_TIMESTAMP = "not-a-date";
    private static final String WITHIN_WINDOW_CREATED_DATE = "2026-10-28T10:15:20.000Z";
    private static final String OUTSIDE_WINDOW_CREATED_DATE = "2026-10-28T10:00:00.000Z";
    private static final String BLANK_TIMESTAMP = "";
    private static final long TEST_MERGE_WINDOW_SECONDS = 60L;
    private static final String TRUE_STRING = "true";
    private static final String FALSE_STRING = "false";
    private static final String OLD_ENTRY_END_DATE = "2020-01-01T00:00:00Z";
    private static final String JUNIOR_ENGINEER = "Junior Engineer";

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private ProjectUtil projectUtil;

    @Mock
    private CbServerProperties cbServerProperties;

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

    private Map<String, Object> buildHistoryEntryWithCreatedDate(String uuid, Object index, String designation,
            String orgName, String orgId, String endDate, String currentlyWorking, String createdDate) {
        Map<String, Object> entry = buildHistoryEntry(uuid, index, designation, orgName, orgId, endDate,
                currentlyWorking);
        entry.put(Constants.CREATED_DATE, createdDate);
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

    private void stubSaveSuccess() throws JsonProcessingException {
        when(mapper.writeValueAsString(any())).thenReturn(EMPTY_JSON);
        ApiResponse success = new ApiResponse();
        success.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE), anyMap())).thenReturn(success);
    }

    private void stubSaveFailure() throws JsonProcessingException {
        when(mapper.writeValueAsString(any())).thenReturn(EMPTY_JSON);
        ApiResponse failure = new ApiResponse();
        failure.put(Constants.RESPONSE, FAILED_RESPONSE);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(failure);
    }

    private void stubMergeWindow() {
        when(cbServerProperties.getServiceHistoryMergeWindowSeconds()).thenReturn(TEST_MERGE_WINDOW_SECONDS);
    }

    private List<WorkflowServiceHistoryEvent> designationChangeEvents() {
        return List.of(
                buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION,
                        DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION,
                        DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));
    }

    private List<WorkflowServiceHistoryEvent> orgTransferEvents() {
        return List.of(
                buildEvent(Constants.ENTRY_TYPE_PREVIOUS, APPROVAL_TYPE_ORG, ORG_TRANSFER_REQUEST, OLD_DESIGNATION,
                        OLD_ORG_NAME, OLD_ORG_ID),
                buildEvent(Constants.ENTRY_TYPE_CURRENT, APPROVAL_TYPE_ORG, ORG_TRANSFER_REQUEST, OLD_DESIGNATION,
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
        @DisplayName("Should return false when events list is empty")
        void processServiceHistoryApprovalEvent_emptyEvents_returnsFalse() {
            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(new ArrayList<>());

            assertFalse(result);
            verify(cassandraOperation, never()).getRecordsByPropertiesByKey(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when events list has only one entry")
        void processServiceHistoryApprovalEvent_singleEntry_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when events list has three entries")
        void processServiceHistoryApprovalEvent_threeEntries_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when PREVIOUS entry is missing")
        void processServiceHistoryApprovalEvent_missingPrevious_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when CURRENT entry is missing")
        void processServiceHistoryApprovalEvent_missingCurrent_returnsFalse() {
            List<WorkflowServiceHistoryEvent> events = List.of(
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

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
                    Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            WorkflowServiceHistoryEvent current = buildEvent(Constants.ENTRY_TYPE_CURRENT,
                    Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME,
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
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, Constants.APPROVAL_TYPE_DESIGNATION,
                            DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should bootstrap two entries (closed PREVIOUS + open CURRENT) for a brand-new user")
        void processServiceHistoryApprovalEvent_noExistingHistory_bootstrapsTwoEntries() throws IOException {
            stubActiveUser();
            stubNoExistingHistory();
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());

            Map<String, Object> closedPrevious = saved.get(0);
            assertEquals(0, closedPrevious.get(Constants.INDEX));
            assertEquals(OLD_DESIGNATION, closedPrevious.get(Constants.DESIGNATION));
            assertEquals(SAME_ORG_ID, closedPrevious.get(Constants.ORG_ID));
            assertEquals(OLD_ORG_NAME, closedPrevious.get(Constants.ORG_NAME));
            assertEquals(FALSE_STRING, closedPrevious.get(Constants.CURRENTLY_WORKING));
            assertEquals(TEST_TIMESTAMP, closedPrevious.get(Constants.END_DATE));
            assertEquals(TEST_TIMESTAMP, closedPrevious.get(Constants.CREATED_DATE));
            assertEquals(TRUE_STRING, closedPrevious.get(Constants.APPROVED_VIA_WORKFLOW));
            assertNotNull(closedPrevious.get(Constants.UUID));

            Map<String, Object> openCurrent = saved.get(1);
            assertEquals(1, openCurrent.get(Constants.INDEX));
            assertEquals(NEW_DESIGNATION, openCurrent.get(Constants.DESIGNATION));
            assertEquals(SAME_ORG_ID, openCurrent.get(Constants.ORG_ID));
            assertEquals(TRUE_STRING, openCurrent.get(Constants.CURRENTLY_WORKING));
            assertEquals(TEST_TIMESTAMP, openCurrent.get(Constants.CREATED_DATE));
        }

        @Test
        @DisplayName("Should return false when bootstrap save fails for a brand-new user")
        void processServiceHistoryApprovalEvent_bootstrapSaveFails_returnsFalse() throws IOException {
            stubActiveUser();
            stubNoExistingHistory();
            stubSaveFailure();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
        }

        @Test
        @DisplayName("Should bootstrap using Instant.now fallback when event timestamp is blank")
        void processServiceHistoryApprovalEvent_bootstrapBlankTimestamp_fallsBackToNow() throws IOException {
            stubActiveUser();
            stubNoExistingHistory();
            stubSaveSuccess();
            WorkflowServiceHistoryEvent previous = buildEvent(Constants.ENTRY_TYPE_PREVIOUS,
                    Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            WorkflowServiceHistoryEvent current = buildEvent(Constants.ENTRY_TYPE_CURRENT,
                    Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            previous.setTimestamp(BLANK_TIMESTAMP);
            current.setTimestamp(BLANK_TIMESTAMP);

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(
                    List.of(previous, current));

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());
            assertTrue(StringUtils.isNotBlank((String) saved.get(0).get(Constants.END_DATE)));
            assertTrue(StringUtils.isNotBlank((String) saved.get(0).get(Constants.CREATED_DATE)));
            assertTrue(StringUtils.isNotBlank((String) saved.get(1).get(Constants.CREATED_DATE)));
        }

        @Test
        @DisplayName("Should return false when highest-index entry does not match PREVIOUS event")
        void processServiceHistoryApprovalEvent_highestEntryMismatch_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> mismatched = buildHistoryEntry(UUID_ZERO, 0, OTHER_DESIGNATION, OLD_ORG_NAME,
                    OLD_ORG_ID, "", TRUE_STRING);
            stubExistingHistory(List.of(mismatched));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should merge in place when within window and orgId matches, updating only designation")
        void processServiceHistoryApprovalEvent_withinWindowOrgIdMatches_mergesDesignationOnly() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING, WITHIN_WINDOW_CREATED_DATE);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();
            stubMergeWindow();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(1, saved.size());

            Map<String, Object> merged = saved.get(0);
            assertEquals(NEW_DESIGNATION, merged.get(Constants.DESIGNATION));
            assertEquals(SAME_ORG_ID, merged.get(Constants.ORG_ID));
            assertEquals(OLD_ORG_NAME, merged.get(Constants.ORG_NAME));
            assertEquals(TRUE_STRING, merged.get(Constants.CURRENTLY_WORKING));
            assertEquals(WITHIN_WINDOW_CREATED_DATE, merged.get(Constants.CREATED_DATE));
            assertEquals(UUID_ZERO, merged.get(Constants.UUID));
        }

        @Test
        @DisplayName("Should fall through to close-and-append when within window but orgId does not match")
        void processServiceHistoryApprovalEvent_withinWindowOrgIdMismatch_fallsToCloseAndAppend() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    OLD_ORG_ID, "", TRUE_STRING, WITHIN_WINDOW_CREATED_DATE);
            stubExistingHistory(new ArrayList<>(List.of(current)));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should fall through to close-and-append when outside the merge window")
        void processServiceHistoryApprovalEvent_outsideWindowOrgIdMatches_fallsToCloseAndAppend() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING, OUTSIDE_WINDOW_CREATED_DATE);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();
            stubMergeWindow();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());
            assertEquals(FALSE_STRING, saved.get(0).get(Constants.CURRENTLY_WORKING));
            assertEquals(1, saved.get(1).get(Constants.INDEX));
            assertEquals(NEW_DESIGNATION, saved.get(1).get(Constants.DESIGNATION));
        }

        @Test
        @DisplayName("Should treat a blank createdDate on highest entry as outside the merge window")
        void processServiceHistoryApprovalEvent_blankCreatedDate_fallsToCloseAndAppend() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING, "");
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            assertEquals(2, contextDataCaptor.getValue().size());
        }

        @Test
        @DisplayName("Should treat an unparseable createdDate on highest entry as outside the merge window")
        void processServiceHistoryApprovalEvent_unparseableCreatedDate_fallsToCloseAndAppend() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING, UNPARSEABLE_TIMESTAMP);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            assertEquals(2, contextDataCaptor.getValue().size());
        }

        @Test
        @DisplayName("Should treat a blank event timestamp as outside the merge window")
        void processServiceHistoryApprovalEvent_blankEventTimestamp_fallsToCloseAndAppend() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING, WITHIN_WINDOW_CREATED_DATE);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();
            WorkflowServiceHistoryEvent previous = buildEvent(Constants.ENTRY_TYPE_PREVIOUS,
                    Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION_CHANGE_REQUEST, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            WorkflowServiceHistoryEvent currentEvent = buildEvent(Constants.ENTRY_TYPE_CURRENT,
                    Constants.APPROVAL_TYPE_DESIGNATION, DESIGNATION_CHANGE_REQUEST, NEW_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID);
            previous.setTimestamp(BLANK_TIMESTAMP);
            currentEvent.setTimestamp(BLANK_TIMESTAMP);

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(
                    List.of(previous, currentEvent));

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            assertEquals(2, contextDataCaptor.getValue().size());
        }

        @Test
        @DisplayName("Should return false when merge save fails")
        void processServiceHistoryApprovalEvent_mergeSaveFails_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntryWithCreatedDate(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING, WITHIN_WINDOW_CREATED_DATE);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveFailure();
            stubMergeWindow();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
        }

        @Test
        @DisplayName("Should close highest-index entry and append new entry on match outside merge window")
        void processServiceHistoryApprovalEvent_match_closesAndAppendsEntry() throws IOException {
            stubActiveUser();
            Map<String, Object> unrelated = buildHistoryEntry(UUID_ZERO, 0, JUNIOR_ENGINEER, OLD_ORG_NAME,
                    OLD_ORG_ID, OLD_ENTRY_END_DATE, FALSE_STRING);
            Map<String, Object> current = buildHistoryEntry(UUID_ONE, 1, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID,
                    "", TRUE_STRING);
            stubExistingHistory(new ArrayList<>(List.of(unrelated, current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(3, saved.size());

            Map<String, Object> closedEntry = saved.get(1);
            assertEquals(FALSE_STRING, closedEntry.get(Constants.CURRENTLY_WORKING));
            assertTrue(StringUtils.isNotBlank((String) closedEntry.get(Constants.END_DATE)));

            Map<String, Object> newEntry = saved.get(2);
            assertEquals(2, newEntry.get(Constants.INDEX));
            assertEquals(NEW_DESIGNATION, newEntry.get(Constants.DESIGNATION));
            assertEquals(SAME_ORG_ID, newEntry.get(Constants.ORG_ID));
            assertEquals(TRUE_STRING, newEntry.get(Constants.CURRENTLY_WORKING));
            assertEquals(TRUE_STRING, newEntry.get(Constants.APPROVED_VIA_WORKFLOW));
            assertNotNull(newEntry.get(Constants.UUID));
            assertEquals(TEST_TIMESTAMP, newEntry.get(Constants.CREATED_DATE));
        }

        @Test
        @DisplayName("Should resolve numeric-string index values and increment correctly")
        void processServiceHistoryApprovalEvent_stringIndex_resolvesAndIncrements() throws IOException {
            stubActiveUser();
            Map<String, Object> lowerIndex = buildHistoryEntry(UUID_ZERO, "0", JUNIOR_ENGINEER, OLD_ORG_NAME,
                    OLD_ORG_ID, OLD_ENTRY_END_DATE, FALSE_STRING);
            Map<String, Object> current = buildHistoryEntry(UUID_ONE, "3", OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING);
            stubExistingHistory(new ArrayList<>(List.of(lowerIndex, current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            Map<String, Object> newEntry = contextDataCaptor.getValue().get(2);
            assertEquals(4, newEntry.get(Constants.INDEX));
        }

        @Test
        @DisplayName("Should treat an unparseable string index as -1 and start the new index at 0")
        void processServiceHistoryApprovalEvent_unparseableStringIndex_defaultsToZero() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry(UUID_ZERO, "not-a-number", OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            Map<String, Object> newEntry = contextDataCaptor.getValue().get(1);
            assertEquals(0, newEntry.get(Constants.INDEX));
        }

        @Test
        @DisplayName("Should default a missing index to -1 and start the new index at 0")
        void processServiceHistoryApprovalEvent_missingIndex_defaultsToZero() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry(UUID_ZERO, null, OLD_DESIGNATION, OLD_ORG_NAME,
                    SAME_ORG_ID, "", TRUE_STRING);
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
        @DisplayName("Should return false when Cassandra insert fails on close-and-append")
        void processServiceHistoryApprovalEvent_saveFails_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID,
                    "", TRUE_STRING);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveFailure();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when JSON serialization fails on save")
        void processServiceHistoryApprovalEvent_jsonSerializationFails_returnsFalse() throws IOException {
            stubActiveUser();
            stubNoExistingHistory();
            when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
                private static final long serialVersionUID = 1L;
            });

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
                    buildEvent(Constants.ENTRY_TYPE_PREVIOUS, APPROVAL_TYPE_ORG, ORG_TRANSFER_REQUEST,
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID),
                    buildEvent(Constants.ENTRY_TYPE_CURRENT, APPROVAL_TYPE_ORG, ORG_TRANSFER_REQUEST,
                            OLD_DESIGNATION, OLD_ORG_NAME, SAME_ORG_ID));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(events);

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should return false when highest-index entry's orgId does not match PREVIOUS")
        void processServiceHistoryApprovalEvent_highestEntryMismatch_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> mismatched = buildHistoryEntry(UUID_ZERO, 0, OLD_DESIGNATION, OTHER_ORG_NAME,
                    OTHER_ORG_ID, "", TRUE_STRING);
            stubExistingHistory(new ArrayList<>(List.of(mismatched)));

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertFalse(result);
            verify(cassandraOperation, never()).insertRecord(any(), any(), any());
        }

        @Test
        @DisplayName("Should close highest-index entry and append new entry, carrying designation unchanged")
        void processServiceHistoryApprovalEvent_match_closesAndAppendsEntry() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME, OLD_ORG_ID,
                    "", TRUE_STRING);
            stubExistingHistory(new ArrayList<>(List.of(current)));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());

            Map<String, Object> closedEntry = saved.get(0);
            assertEquals(FALSE_STRING, closedEntry.get(Constants.CURRENTLY_WORKING));
            assertTrue(StringUtils.isNotBlank((String) closedEntry.get(Constants.END_DATE)));

            Map<String, Object> newEntry = saved.get(1);
            assertEquals(1, newEntry.get(Constants.INDEX));
            assertEquals(OLD_DESIGNATION, newEntry.get(Constants.DESIGNATION));
            assertEquals(NEW_ORG_ID, newEntry.get(Constants.ORG_ID));
            assertEquals(NEW_ORG_NAME, newEntry.get(Constants.ORG_NAME));
            assertEquals(TRUE_STRING, newEntry.get(Constants.CURRENTLY_WORKING));
            assertEquals(TRUE_STRING, newEntry.get(Constants.APPROVED_VIA_WORKFLOW));
        }

        @Test
        @DisplayName("Should bootstrap two entries (closed PREVIOUS + open CURRENT) for a brand-new user")
        void processServiceHistoryApprovalEvent_noExistingHistory_bootstrapsTwoEntries() throws IOException {
            stubActiveUser();
            stubNoExistingHistory();
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(orgTransferEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());

            Map<String, Object> closedPrevious = saved.get(0);
            assertEquals(0, closedPrevious.get(Constants.INDEX));
            assertEquals(OLD_ORG_ID, closedPrevious.get(Constants.ORG_ID));
            assertEquals(OLD_ORG_NAME, closedPrevious.get(Constants.ORG_NAME));
            assertEquals(FALSE_STRING, closedPrevious.get(Constants.CURRENTLY_WORKING));

            Map<String, Object> openCurrent = saved.get(1);
            assertEquals(1, openCurrent.get(Constants.INDEX));
            assertEquals(NEW_ORG_ID, openCurrent.get(Constants.ORG_ID));
            assertEquals(NEW_ORG_NAME, openCurrent.get(Constants.ORG_NAME));
            assertEquals(TRUE_STRING, openCurrent.get(Constants.CURRENTLY_WORKING));
        }

        @Test
        @DisplayName("Should return false when Cassandra insert fails")
        void processServiceHistoryApprovalEvent_saveFails_returnsFalse() throws IOException {
            stubActiveUser();
            Map<String, Object> current = buildHistoryEntry(UUID_ZERO, 0, OLD_DESIGNATION, OLD_ORG_NAME, OLD_ORG_ID,
                    "", TRUE_STRING);
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
        @DisplayName("Should treat unparseable contextData as empty history and bootstrap two entries")
        void processServiceHistoryApprovalEvent_unparseableContextData_bootstrapsTwoEntries() throws IOException {
            stubActiveUser();
            Map<String, Object> row = new HashMap<>();
            row.put(Constants.CONTEXT_DATA, PLACEHOLDER_JSON);
            when(cassandraOperation.getRecordsByPropertiesByKey(eq(Constants.KEYSPACE_SUNBIRD),
                    eq(Constants.TABLE_USER_EXTENDED_PROFILE), anyMap(), isNull(), isNull()))
                    .thenReturn(List.of(row));
            when(projectUtil.parseListOfMap(PLACEHOLDER_JSON)).thenThrow(new IOException(BAD_JSON_MESSAGE));
            stubSaveSuccess();

            boolean result = serviceHistoryApprovalService.processServiceHistoryApprovalEvent(designationChangeEvents());

            assertTrue(result);
            verify(mapper).writeValueAsString(contextDataCaptor.capture());
            List<Map<String, Object>> saved = contextDataCaptor.getValue();
            assertEquals(2, saved.size());
            assertEquals(0, saved.get(0).get(Constants.INDEX));
            assertEquals(OLD_DESIGNATION, saved.get(0).get(Constants.DESIGNATION));
            assertEquals(1, saved.get(1).get(Constants.INDEX));
            assertEquals(NEW_DESIGNATION, saved.get(1).get(Constants.DESIGNATION));
        }
    }
}
