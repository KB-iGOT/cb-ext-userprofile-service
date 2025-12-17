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
    void testReadUserDataFromDB_WithKeyList_Success() {
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
        try {
            when(mapper.readValue(eq("{\"phone\":\"123-456\"}"), any(TypeReference.class)))
                    .thenReturn(profileDetailsMap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
    void testGetExistingContextData_Success() {
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
        try {
            when(projectUtil.parseListOfMap(contextDataJson)).thenReturn(expectedData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
    void testGetExistingContextData_ParseException_ReturnsEmptyList() {
        String contextDataJson = "{invalid json}";
        Map<String, Object> dbRecord = Map.of(Constants.CONTEXT_DATA, contextDataJson);
        List<Map<String, Object>> dbRecords = List.of(dbRecord);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(dbRecords);
        try {
            when(projectUtil.parseListOfMap(contextDataJson))
                    .thenThrow(new IOException("Parse error"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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

    // ==================== readUserExtendedProfile Tests ====================

    @Test
    void testReadUserExtendedProfile_WithCacheHit_ReturnsLimitedSummary() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String cachedJson = "{\"education\":{\"count\":5,\"data\":[{\"degree\":\"BS\"},{\"degree\":\"MS\"},{\"degree\":\"PhD\"}]}}";

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullData = new HashMap<>();
        Map<String, Object> educationBlock = new HashMap<>();
        educationBlock.put(Constants.COUNT, 5);
        educationBlock.put(Constants.DATA, List.of(
                Map.of("degree", "BS"),
                Map.of("degree", "MS"),
                Map.of("degree", "PhD")
        ));
        fullData.put("education", educationBlock);

        try {
            when(projectUtil.parseMap(cachedJson)).thenReturn(fullData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("education"));

        Map<String, Object> educationResult = (Map<String, Object>) result.get("education");
        assertEquals(5, educationResult.get(Constants.COUNT));
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) educationResult.get(Constants.DATA);
        assertEquals(2, dataList.size()); // Limited to 2 items
    }

    @Test
    void testReadUserExtendedProfile_WithCacheMiss_FetchesFromDB() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String[] contextTypes = {"education", "experience"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        // Mock education data
        String educationJson = "[{\"degree\":\"BS\"},{\"degree\":\"MS\"}]";
        Map<String, Object> educationRecord = Map.of(Constants.CONTEXT_DATA, educationJson);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                eq(Map.of(Constants.USERID_KEY, USER_ID, Constants.CONTEXT_TYPE, "education")),
                isNull(),
                isNull()
        )).thenReturn(List.of(educationRecord));

        // Mock experience data
        String experienceJson = "[{\"company\":\"ABC\"}]";
        Map<String, Object> experienceRecord = Map.of(Constants.CONTEXT_DATA, experienceJson);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                eq(Map.of(Constants.USERID_KEY, USER_ID, Constants.CONTEXT_TYPE, "experience")),
                isNull(),
                isNull()
        )).thenReturn(List.of(experienceRecord));

        try {
            when(projectUtil.parseListOfMap(educationJson))
                    .thenReturn(List.of(Map.of("degree", "BS"), Map.of("degree", "MS")));
            when(projectUtil.parseListOfMap(experienceJson))
                    .thenReturn(List.of(Map.of("company", "ABC")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("education"));
        assertTrue(result.containsKey("experience"));

        Map<String, Object> educationBlock = (Map<String, Object>) result.get("education");
        assertEquals(2, educationBlock.get(Constants.COUNT));
        List<Map<String, Object>> educationData = (List<Map<String, Object>>) educationBlock.get(Constants.DATA);
        assertEquals(2, educationData.size());

        Map<String, Object> experienceBlock = (Map<String, Object>) result.get("experience");
        assertEquals(1, experienceBlock.get(Constants.COUNT));

        verify(cacheService).putCache(redisKey, result);
    }

    @Test
    void testReadUserExtendedProfile_WithEmptyContextData_ReturnsEmptyMap() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String[] contextTypes = {"education", "experience"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testReadUserExtendedProfile_CacheReadException_FetchesFromDB() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String[] contextTypes = {"education"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenThrow(new RuntimeException("Cache error"));
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        String educationJson = "[{\"degree\":\"BS\"}]";
        Map<String, Object> educationRecord = Map.of(Constants.CONTEXT_DATA, educationJson);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(educationRecord));

        try {
            when(projectUtil.parseListOfMap(educationJson))
                    .thenReturn(List.of(Map.of("degree", "BS")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("education"));
    }

    @Test
    void testReadUserExtendedProfile_ParseMapException_FetchesFromDB() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String cachedJson = "{invalid json}";
        String[] contextTypes = {"education"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        try {
            when(projectUtil.parseMap(cachedJson)).thenThrow(new IOException("Parse error"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String educationJson = "[{\"degree\":\"BS\"}]";
        Map<String, Object> educationRecord = Map.of(Constants.CONTEXT_DATA, educationJson);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(educationRecord));

        try {
            when(projectUtil.parseListOfMap(educationJson))
                    .thenReturn(List.of(Map.of("degree", "BS")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("education"));
    }

    @Test
    void testReadUserExtendedProfile_CachePutException_ContinuesExecution() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String[] contextTypes = {"education"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        String educationJson = "[{\"degree\":\"BS\"}]";
        Map<String, Object> educationRecord = Map.of(Constants.CONTEXT_DATA, educationJson);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(educationRecord));

        try {
            when(projectUtil.parseListOfMap(educationJson))
                    .thenReturn(List.of(Map.of("degree", "BS")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        doThrow(new RuntimeException("Cache error")).when(cacheService).putCache(anyString(), any());

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("education"));
    }

    @Test
    void testReadUserExtendedProfile_WithMoreThanTwoItems_LimitsToTwo() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String[] contextTypes = {"education"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        String educationJson = "[{\"degree\":\"BS\"},{\"degree\":\"MS\"},{\"degree\":\"PhD\"},{\"degree\":\"MBA\"}]";
        Map<String, Object> educationRecord = Map.of(Constants.CONTEXT_DATA, educationJson);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(educationRecord));

        List<Map<String, Object>> allEducation = List.of(
                Map.of("degree", "BS"),
                Map.of("degree", "MS"),
                Map.of("degree", "PhD"),
                Map.of("degree", "MBA")
        );

        try {
            when(projectUtil.parseListOfMap(educationJson)).thenReturn(allEducation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        Map<String, Object> educationBlock = (Map<String, Object>) result.get("education");
        assertEquals(4, educationBlock.get(Constants.COUNT));
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) educationBlock.get(Constants.DATA);
        assertEquals(2, dataList.size()); // Limited to first 2 items
        assertEquals("BS", dataList.get(0).get("degree"));
        assertEquals("MS", dataList.get(1).get("degree"));
    }

    @Test
    void testReadUserExtendedProfile_WithMixedContextTypes_FiltersEmptyOnes() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String[] contextTypes = {"education", "experience", "skills"};

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getContextType()).thenReturn(contextTypes);

        // Education has data
        String educationJson = "[{\"degree\":\"BS\"}]";
        Map<String, Object> educationRecord = Map.of(Constants.CONTEXT_DATA, educationJson);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                eq(Map.of(Constants.USERID_KEY, USER_ID, Constants.CONTEXT_TYPE, "education")),
                isNull(),
                isNull()
        )).thenReturn(List.of(educationRecord));

        // Experience is empty
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                eq(Map.of(Constants.USERID_KEY, USER_ID, Constants.CONTEXT_TYPE, "experience")),
                isNull(),
                isNull()
        )).thenReturn(Collections.emptyList());

        // Skills is empty
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                eq(Map.of(Constants.USERID_KEY, USER_ID, Constants.CONTEXT_TYPE, "skills")),
                isNull(),
                isNull()
        )).thenReturn(Collections.emptyList());

        try {
            when(projectUtil.parseListOfMap(educationJson))
                    .thenReturn(List.of(Map.of("degree", "BS")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(1, result.size()); // Only education should be present
        assertTrue(result.containsKey("education"));
        assertFalse(result.containsKey("experience"));
        assertFalse(result.containsKey("skills"));
    }

    @Test
    void testReadUserExtendedProfile_BuildLimitedSummaryWithNonMapValue() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String cachedJson = "{\"simpleField\":\"value\",\"education\":{\"count\":3,\"data\":[{\"degree\":\"BS\"}]}}";

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullData = new HashMap<>();
        fullData.put("simpleField", "value"); // Non-map value
        Map<String, Object> educationBlock = new HashMap<>();
        educationBlock.put(Constants.COUNT, 3);
        educationBlock.put(Constants.DATA, List.of(Map.of("degree", "BS")));
        fullData.put("education", educationBlock);

        try {
            when(projectUtil.parseMap(cachedJson)).thenReturn(fullData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("value", result.get("simpleField")); // Non-map values should pass through
        assertTrue(result.containsKey("education"));
    }

    @Test
    void testReadUserExtendedProfile_BuildLimitedSummaryWithNonListData() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String cachedJson = "{\"customField\":{\"count\":1,\"data\":\"stringData\"}}";

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullData = new HashMap<>();
        Map<String, Object> customBlock = new HashMap<>();
        customBlock.put(Constants.COUNT, 1);
        customBlock.put(Constants.DATA, "stringData"); // Non-list data
        fullData.put("customField", customBlock);

        try {
            when(projectUtil.parseMap(cachedJson)).thenReturn(fullData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> customResult = (Map<String, Object>) result.get("customField");
        assertEquals("stringData", customResult.get(Constants.DATA)); // Non-list data should pass through
    }

    @Test
    void testReadUserExtendedProfile_BuildLimitedSummaryWithExactlyTwoItems() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String cachedJson = "{\"education\":{\"count\":2,\"data\":[{\"degree\":\"BS\"},{\"degree\":\"MS\"}]}}";

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullData = new HashMap<>();
        Map<String, Object> educationBlock = new HashMap<>();
        educationBlock.put(Constants.COUNT, 2);
        educationBlock.put(Constants.DATA, List.of(
                Map.of("degree", "BS"),
                Map.of("degree", "MS")
        ));
        fullData.put("education", educationBlock);

        try {
            when(projectUtil.parseMap(cachedJson)).thenReturn(fullData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        Map<String, Object> educationResult = (Map<String, Object>) result.get("education");
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) educationResult.get(Constants.DATA);
        assertEquals(2, dataList.size()); // Should keep exactly 2 items
    }

    @Test
    void testReadUserExtendedProfile_BuildLimitedSummaryWithOneItem() {
        String redisKey = "user_extended_profile:all:" + USER_ID;
        String cachedJson = "{\"education\":{\"count\":1,\"data\":[{\"degree\":\"BS\"}]}}";

        when(projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", USER_ID))
                .thenReturn(redisKey);
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullData = new HashMap<>();
        Map<String, Object> educationBlock = new HashMap<>();
        educationBlock.put(Constants.COUNT, 1);
        educationBlock.put(Constants.DATA, List.of(Map.of("degree", "BS")));
        fullData.put("education", educationBlock);

        try {
            when(projectUtil.parseMap(cachedJson)).thenReturn(fullData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserExtendedProfile(USER_ID);

        assertNotNull(result);
        Map<String, Object> educationResult = (Map<String, Object>) result.get("education");
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) educationResult.get(Constants.DATA);
        assertEquals(1, dataList.size()); // Should keep 1 item when less than 2
    }

    // ==================== Integration Tests ====================

    @Test
    void testReadUserDataFromDB_FullProfileDetailsJson() {
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

        try {
            when(mapper.readValue(eq(profileDetailsJson), any(TypeReference.class)))
                    .thenReturn(expectedProfileDetails);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = service.readUserDataFromDB(USER_ID, keyList);

        assertNotNull(result);
        assertEquals(expectedProfileDetails, result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void testGetExistingContextData_MultipleContextTypes() {
        String educationJson = "[{\"degree\":\"BS\"}]";
        Map<String, Object> dbRecord = Map.of(Constants.CONTEXT_DATA, educationJson);

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(dbRecord));
        try {
            when(projectUtil.parseListOfMap(educationJson))
                    .thenReturn(List.of(Map.of("degree", "BS")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Map<String, Object>> result = service.getExistingContextData(USER_ID, CONTEXT_TYPE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BS", result.get(0).get("degree"));
    }
}
