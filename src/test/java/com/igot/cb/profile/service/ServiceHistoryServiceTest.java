package com.igot.cb.profile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceHistoryServiceTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ProjectUtil projectUtil;

    @Mock
    private ProfileServiceImpl profileServiceImpl;

    private ServiceHistoryService serviceHistoryService;

    private static final String USER_ID = "user-123";
    private static final String WF_ID = "wf-456";
    private static final String DESIGNATION = "Additional Chief Mechanical Engineer";
    private static final String ORG_NAME = "Health and Family Welfare Department Karnataka";
    private static final String ROOT_ORG_ID = "root-org-789";

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper so serviceHistory entries round-trip exactly like production.
        serviceHistoryService = new ServiceHistoryService(cassandraOperation, projectUtil, new ObjectMapper(),
                profileServiceImpl);
    }

    private void mockNoExistingServiceHistory() {
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of());
    }

    private void mockExistingServiceHistory(List<Map<String, Object>> existingEntries) throws IOException {
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.CONTEXT_DATA, "stub-json");
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(row));
        when(projectUtil.parseListOfMap("stub-json")).thenReturn(existingEntries);
    }

    private void mockRootOrgId(String rootOrgId) {
        Map<String, Object> userData = new HashMap<>();
        if (rootOrgId != null) {
            userData.put(Constants.ROOT_ORG_ID, rootOrgId);
        }
        when(profileServiceImpl.readUserDataFromDB(USER_ID, List.of(Constants.ROOT_ORG_ID))).thenReturn(userData);
    }

    private void mockSuccessfulInsert() {
        ApiResponse response = new ApiResponse();
        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePersistedNewEntry() throws Exception {
        ArgumentCaptor<Map<String, Object>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(anyString(), anyString(), queryCaptor.capture());
        String persistedJson = (String) queryCaptor.getValue().get(Constants.CONTEXT_DATA);
        List<Map<String, Object>> persistedList = new ObjectMapper().readValue(persistedJson,
                new TypeReference<List<Map<String, Object>>>() {
                });
        return persistedList.get(persistedList.size() - 1);
    }

    @Test
    void appendDesignationApprovalToServiceHistory_noExistingHistory_appendsNewEntryAndReturnsTrue() throws Exception {
        mockNoExistingServiceHistory();
        mockRootOrgId(ROOT_ORG_ID);
        mockSuccessfulInsert();

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertTrue(result);
        Map<String, Object> newEntry = capturePersistedNewEntry();
        assertEquals(ORG_NAME, newEntry.get(Constants.ORG_NAME));
        assertEquals(DESIGNATION, newEntry.get(Constants.DESIGNATION));
        assertEquals(ROOT_ORG_ID, newEntry.get(Constants.ROOT_ORG_ID));
        assertEquals(WF_ID, newEntry.get(Constants.WF_IF));
        assertEquals(Constants.FALSE, newEntry.get(Constants.CURRENTLY_WORKING));
        assertEquals(Boolean.TRUE, newEntry.get(Constants.APPROVED_VIA_WORKFLOW));
        assertTrue(newEntry.containsKey(Constants.UUID));
    }

    @Test
    void appendDesignationApprovalToServiceHistory_existingEntriesPresent_onlyNewEntryAppended() throws Exception {
        Map<String, Object> existingEntry = new HashMap<>();
        existingEntry.put(Constants.ORG_NAME, "Existing Org");
        existingEntry.put(Constants.DESIGNATION, "Existing Designation");
        existingEntry.put(Constants.WF_IF, "wf-already-processed");
        mockExistingServiceHistory(new ArrayList<>(List.of(existingEntry)));
        mockRootOrgId(ROOT_ORG_ID);
        mockSuccessfulInsert();

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertTrue(result);
        ArgumentCaptor<Map<String, Object>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(anyString(), anyString(), queryCaptor.capture());
        String persistedJson = (String) queryCaptor.getValue().get(Constants.CONTEXT_DATA);
        List<Map<String, Object>> persistedList = new ObjectMapper().readValue(persistedJson,
                new TypeReference<List<Map<String, Object>>>() {
                });
        assertEquals(2, persistedList.size());
        assertEquals("Existing Org", persistedList.get(0).get(Constants.ORG_NAME));
        assertEquals("Existing Designation", persistedList.get(0).get(Constants.DESIGNATION));
        assertEquals(ORG_NAME, persistedList.get(1).get(Constants.ORG_NAME));
    }

    @Test
    void appendDesignationApprovalToServiceHistory_duplicateWfId_skipsAppendAndReturnsTrue() throws Exception {
        Map<String, Object> existingEntry = new HashMap<>();
        existingEntry.put(Constants.ORG_NAME, "Already Recorded Org");
        existingEntry.put(Constants.WF_IF, WF_ID);
        mockExistingServiceHistory(new ArrayList<>(List.of(existingEntry)));

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertTrue(result);
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
        verify(profileServiceImpl, never()).readUserDataFromDB(anyString(), any());
    }

    @Test
    void appendDesignationApprovalToServiceHistory_rootOrgIdNotFound_entryStoresEmptyRootOrgId() throws Exception {
        mockNoExistingServiceHistory();
        mockRootOrgId(null);
        mockSuccessfulInsert();

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertTrue(result);
        Map<String, Object> newEntry = capturePersistedNewEntry();
        assertEquals("", newEntry.get(Constants.ROOT_ORG_ID));
    }

    @Test
    void appendDesignationApprovalToServiceHistory_cassandraInsertFails_returnsFalse() {
        mockNoExistingServiceHistory();
        mockRootOrgId(ROOT_ORG_ID);
        ApiResponse failureResponse = new ApiResponse();
        failureResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        failureResponse.put(Constants.RESPONSE, "FAILED");
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failureResponse);

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertFalse(result);
    }

    @Test
    void appendDesignationApprovalToServiceHistory_unexpectedException_returnsFalseAndDoesNotThrow() {
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenThrow(new RuntimeException("Cassandra unavailable"));

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertFalse(result);
    }

    @Test
    void appendDesignationApprovalToServiceHistory_unparsableExistingData_treatsAsEmptyAndAppendsEntry()
            throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.CONTEXT_DATA, "not-valid-json");
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(row));
        when(projectUtil.parseListOfMap("not-valid-json")).thenThrow(new IOException("bad json"));
        mockRootOrgId(ROOT_ORG_ID);
        mockSuccessfulInsert();

        boolean result = serviceHistoryService.appendDesignationApprovalToServiceHistory(USER_ID, WF_ID, DESIGNATION,
                ORG_NAME);

        assertTrue(result);
        Map<String, Object> newEntry = capturePersistedNewEntry();
        assertNotNull(newEntry);
        assertEquals(ORG_NAME, newEntry.get(Constants.ORG_NAME));
    }
}
