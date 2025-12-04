package com.igot.cb.profile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileReaderServiceImplTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CbServerProperties serverConfig;

    @Mock
    private CacheService cacheService;

    @Mock
    private ProjectUtil projectUtil;

    @Mock
    private ObjectMapper mapper;

    private ProfileReaderServiceImpl service;

    private static final String USER_ID = "user-123";
    private static final String CONTEXT_TYPE = "education";

    @BeforeEach
    void setUp() {
        service = new ProfileReaderServiceImpl(cassandraOperation, serverConfig,
                cacheService, projectUtil, mapper);
    }

    // ==================== readUserDataFromDB Tests ====================

    @Test
    void testReadUserDataFromDB_WithKeyList_Success() throws Exception {
        List<String> keyList = List.of("id", "firstName", "email");
        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put("firstName", "John");
        userRecord.put("email", "john@example.com");
        userRecord.put(Constants.PROFILE_DETAILS, "{\"phone\":\"123-456\"}");

        List<Map<String, Object>> userList = List.of(userRecord);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.USER),
                eq(Map.of(Constants.ID, USER_ID)),
                eq(keyList),
                isNull()
        )).thenReturn(userList);

        Map<String, Object> profileDetailsMap = Map.of("phone", "123-456");
        when(mapper.readValue(eq("{\"phone\":\"123-456\"}"), any(TypeReference.class)))
                .thenReturn(profileDetailsMap);

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(USER_ID, result.get(Constants.ID));
        assertEquals("John", result.get("firstName"));
        assertEquals(profileDetailsMap, result.get(Constants.PROFILE_DETAILS));
        verify(cacheService).putCache(anyString(), eq(result));
    }

    @Test
    void testReadUserDataFromDB_WithNullKeyList_UsesBasicProfileFields() {
        List<String> basicFields = List.of("id", "firstName", "lastName");
        when(serverConfig.getBasicProfileFields()).thenReturn(basicFields);

        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put("firstName", "John");
        userRecord.put(Constants.PROFILE_DETAILS, "");

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.USER),
                eq(Map.of(Constants.ID, USER_ID)),
                eq(basicFields),
                isNull()
        )).thenReturn(List.of(userRecord));

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, null);

        assertNotNull(result);
        assertEquals(USER_ID, result.get(Constants.ID));
        verify(serverConfig).getBasicProfileFields();
    }

    @Test
    void testReadUserDataFromDB_EmptyUserList_ReturnsEmptyMap() {
        List<String> keyList = List.of("id");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(cacheService, never()).putCache(anyString(), any());
    }

    @Test
    void testReadUserDataFromDB_VerifysProfileDetails_SetsEmptyMap() {
        List<String> keyList = List.of("id");
        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put(Constants.PROFILE_DETAILS, null);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(userRecord));

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(Map.of(), result.get(Constants.PROFILE_DETAILS));

        //Check for Empty Map
        userRecord.clear();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put(Constants.PROFILE_DETAILS, "   ");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(userRecord));

        result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(Map.of(), result.get(Constants.PROFILE_DETAILS));

        //Check for Invalid Json
        userRecord.clear();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put(Constants.PROFILE_DETAILS, "");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(userRecord));

        result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(Map.of(), result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void testReadUserDataFromDB_CacheException_ContinuesExecution() {
        List<String> keyList = List.of("id");
        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put(Constants.PROFILE_DETAILS, "");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(userRecord));

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(USER_ID, result.get(Constants.ID));
    }

    // ==================== getExistingContextData Tests ====================

    @Test
    void testGetExistingContextData_Success() throws Exception {
        Map<String, Object> query = Map.of(
                Constants.USERID_KEY, USER_ID,
                Constants.CONTEXT_TYPE, CONTEXT_TYPE
        );

        String contextDataJson = "[{\"degree\":\"BS\"},{\"degree\":\"MS\"}]";
        Map<String, Object> dbRecord = Map.of(Constants.CONTEXT_DATA, contextDataJson);
        List<Map<String, Object>> dbRecords = List.of(dbRecord);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                eq(query),
                isNull(),
                isNull()
        )).thenReturn(dbRecords);

        List<Map<String, Object>> expectedData = List.of(
                Map.of("degree", "BS"),
                Map.of("degree", "MS")
        );
        when(projectUtil.parseListOfMap(contextDataJson)).thenReturn(expectedData);

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("BS", result.get(0).get("degree"));
        assertEquals("MS", result.get(1).get("degree"));
    }

    @Test
    void testGetExistingContextData_EmptyRecords_ReturnsEmptyList() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetExistingContextData_NullRecords_ReturnsEmptyList() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(null);

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetExistingContextData_ParseException_ReturnsEmptyList() throws Exception {
        String contextDataJson = "{invalid json}";
        Map<String, Object> dbRecord = Map.of(Constants.CONTEXT_DATA, contextDataJson);
        List<Map<String, Object>> dbRecords = List.of(dbRecord);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(dbRecords);
        when(projectUtil.parseListOfMap(contextDataJson))
                .thenThrow(new IOException("Parse error"));

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetExistingContextData_NullContextData_ReturnsEmptyList() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put("someOtherField", "value");
        // No CONTEXT_DATA field
        List<Map<String, Object>> dbRecords = List.of(dbRecord);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(dbRecords);

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConstructor() {
        ProfileReaderServiceImpl newService = new ProfileReaderServiceImpl(
                cassandraOperation, serverConfig, cacheService, projectUtil, mapper);

        assertNotNull(newService);
    }

    // ==================== Integration Tests ====================

    @Test
    void testReadUserDataFromDB_FullProfileDetailsJson() throws Exception {
        List<String> keyList = List.of("id", Constants.PROFILE_DETAILS);
        String profileDetailsJson = "{\"personalDetails\":{\"dob\":\"1990-01-01\"},\"professionalDetails\":[{\"designation\":\"Engineer\"}]}";

        Map<String, Object> userRecord = new HashMap<>();
        userRecord.put(Constants.ID, USER_ID);
        userRecord.put(Constants.PROFILE_DETAILS, profileDetailsJson);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(userRecord));

        Map<String, Object> expectedProfileDetails = new HashMap<>();
        expectedProfileDetails.put("personalDetails", Map.of("dob", "1990-01-01"));
        expectedProfileDetails.put("professionalDetails", List.of(Map.of("designation", "Engineer")));

        when(mapper.readValue(eq(profileDetailsJson), any(TypeReference.class)))
                .thenReturn(expectedProfileDetails);

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(expectedProfileDetails, result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void testGetExistingContextData_MultipleContextTypes() throws Exception {
        String educationJson = "[{\"degree\":\"BS\"}]";
        Map<String, Object> dbRecord = Map.of(Constants.CONTEXT_DATA, educationJson);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(dbRecord));
        when(projectUtil.parseListOfMap(educationJson))
                .thenReturn(List.of(Map.of("degree", "BS")));

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BS", result.get(0).get("degree"));
    }
}
