package com.igot.cb.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.service.AchievementServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AchievementServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CbServerProperties cbServerProperties;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private EsClientService esClientService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RedisTemplate<String, SearchResult> redisTemplate;
    @Mock
    private ValueOperations<String, SearchResult> valueOperations;
    @Mock
    private CacheService cacheService;

    @InjectMocks
    private AchievementServiceImpl achievementService;

    @BeforeEach
    void setUp() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            when(cbServerProperties.getRequiredFieldsProperty()).thenReturn("id,contextType");
            when(cbServerProperties.getContextType()).thenReturn(new String[]{"testContext"});
            when(cbServerProperties.getAchievementsMandatoryFields()).thenReturn("field1,field2");
            when(cbServerProperties.getAchievementEsRequiredFieldsMappingPath()).thenReturn("/tmp/mapping.json");
            when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(1000L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            achievementService = new AchievementServiceImpl(
                    accessTokenValidator, cbServerProperties, cassandraOperation,
                    esClientService, objectMapper, redisTemplate, cacheService
            );
            java.lang.reflect.Method initMethod = AchievementServiceImpl.class.getDeclaredMethod("initRequiredFields");
            initMethod.setAccessible(true);
            initMethod.invoke(achievementService);
        }
    }

    // ==================== CREATE TESTS ====================

    @Test
    void testCreateLearnerAchievement_success() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        // Mock searchDocuments for refreshAchievementSearchCacheForUser
        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(cassandraOperation, times(1)).insertRecord(any(), any(), any());
        verify(esClientService, times(1)).addDocument(any(), any(), any(), any(), any());
        verify(esClientService, times(5)).searchDocuments(any(), any()); // Called 5 times in refreshAchievementSearchCacheForUser
    }

    @Test
    void testCreateLearnerAchievement_userIdNotFound() {
        Map<String, Object> request = new HashMap<>();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCreateLearnerAchievement_validationError() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "invalid");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCreateLearnerAchievement_cassandraFail() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(cassandraOperation.insertRecord(any(), any(), any())).thenThrow(new RuntimeException("Cassandra error"));

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    void testUpdateLearnerAchievement_success() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.isRequireEs()).thenReturn(true);
        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.CONTEXT_DATA, "{}");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(new SearchResult());
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(esClientService, times(1)).updateDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateLearnerAchievement_userIdNotFound() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_validationError() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "invalid");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_missingIdOrContextType() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_contextDataMissing() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, null);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_recordNotFound() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_cassandraFail() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.CONTEXT_DATA, "{}");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== DELETE TESTS ====================

    @Test
    void testDeleteLearnerAchievement_success() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.deleteRecordByCompositeKey(any(), any(), any())).thenReturn(cassandraResponse);
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(new SearchResult());
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("Achievement deleted successfully", response.getResult().get("message"));
        verify(cassandraOperation, times(1)).deleteRecordByCompositeKey(any(), any(), any());
    }

    @Test
    void testDeleteLearnerAchievement_invalidRequest() {
        ApiResponse response = achievementService.deleteLearnerAchievement(null, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testDeleteLearnerAchievement_missingFields() {
        Map<String, Object> reqMap = new HashMap<>();
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testDeleteLearnerAchievement_userIdNotFound() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testDeleteLearnerAchievement_cassandraFail() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, "fail");
        when(cassandraOperation.deleteRecordByCompositeKey(any(), any(), any())).thenReturn(cassandraResponse);
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== READ TESTS ====================

    @Test
    void testReadLearnerAchievement_success() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.ID, "achv1");
        achievement.put(Constants.CONTEXT_TYPE, "testContext");
        achievement.put(Constants.CONTEXT_DATA, "{}");

        when(cacheService.getCache(any())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(achievement));
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = achievementService.readLearnerAchievement("achv1", "token", "testContext");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(cassandraOperation, times(1)).getRecordsByPropertiesByKey(any(), any(), any(), any(), any());
    }

    @Test
    void testReadLearnerAchievement_userIdNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.readLearnerAchievement("achv1", "token", "testContext");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadLearnerAchievement_missingAchievementId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.readLearnerAchievement("", "token", "testContext");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadLearnerAchievement_notFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(any())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        ApiResponse response = achievementService.readLearnerAchievement("achv1", "token", "testContext");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    // ==================== STATUS UPDATE TESTS ====================

    @Test
    void testStatusUpdateLearnerAchievement_success() throws Exception {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "approved");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, Constants.PENDING);
        recordMap.put(Constants.CREATED_ON, LocalDate.now());
        recordMap.put("id", "achv1");
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(recordMap));
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any())).thenReturn(cassandraResponse);
        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        esDoc.put(Constants.STATUS, "PENDING");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);

        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");

        assertEquals("Achievement status updated successfully", response.getResult().get("message"));
        verify(cassandraOperation, times(1)).updateRecordByCompositeKey(any(), any(), any(), any());
    }

    @Test
    void testStatusUpdateLearnerAchievement_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        Map<String, Object> request = new HashMap<>();
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_invalidRequest() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> request = new HashMap<>();
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_recordNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "test");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_statusNotPending() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "test");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, "APPROVED");
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt())).thenReturn(Collections.singletonList(recordMap));
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_cassandraFail() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "test");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, Constants.PENDING);
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt())).thenReturn(Collections.singletonList(recordMap));
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, "fail");
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any())).thenReturn(cassandraResponse);
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== SEARCH TESTS ====================

    @Test
    void testSearchLearnerAchievements_success() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.USER_ID, "user123");
        data.add(achievement);
        searchResult.setData(data);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);
        when(cacheService.hget(any())).thenReturn(Collections.emptyList());
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
        verify(esClientService, times(1)).searchDocuments(any(), any());
    }

    @Test
    void testSearchLearnerAchievements_fromCache() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult cachedResult = new SearchResult();

        when(valueOperations.get(anyString())).thenReturn(cachedResult);

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
        verify(esClientService, never()).searchDocuments(any(), any());
    }

    @Test
    void testSearchLearnerAchievements_minimumCharacters() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("ab");
        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testSearchLearnerAchievements_noDataFound() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();
        searchResult.setData(Collections.emptyList());

        when(valueOperations.get(anyString())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
    }

    @Test
    void testSearchLearnerAchievements_esException() throws Exception {
        SearchCriteria criteria = new SearchCriteria();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenThrow(new RuntimeException("ES error"));

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetAchievementFromCache_success() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        when(cacheService.getCache(any())).thenReturn("{\"key\":\"value\"}");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(map);

        Map<String, Object> result =
                achievementService.readLearnerAchievement("id1", "token", "testContext").getResult();

        assertNotNull(result);
    }

    @Test
    void testGetAchievementFromCache_deserializationFail() throws Exception {
        when(cacheService.getCache(any())).thenReturn("invalid-json");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("JSON error"));

        Map<String, Object> result =
                achievementService.readLearnerAchievement("id1", "token", "testContext").getResult();

        assertNotNull(result); // should fallback
    }

    @Test
    void testValidateRequest_missingContextType() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_DATA, new HashMap<>());

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testValidateRequest_blankMandatoryField() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "");  // blank
        contextData.put("field2", "value");

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateAchievementInES_contextDataString() throws Exception {
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.STATUS, Constants.PENDING);
        record.put("contextdata", "{\"key\":\"value\"}");
        record.put(Constants.CREATED_ON, LocalDate.now());

        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(record));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        when(esClientService.readDocument(any(), any()))
                .thenReturn(new HashMap<>());

        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any()))
                .thenReturn(cassandraResponse);

        Map<String, Object> request = new HashMap<>();
        Map<String, Object> req = new HashMap<>();
        req.put("id", "1");
        req.put("contextType", "testContext");
        req.put("learnerId", "user");
        req.put(Constants.STATUS, "APPROVED");
        req.put("reason", "ok");
        request.put(Constants.REQUEST, req);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user");

        achievementService.statusUpdateLearnerAchievement(request, "token");

        verify(esClientService).updateDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testGenerateRedisJwtTokenKey_exception() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("error") {});

        String key = achievementService.generateRedisJwtTokenKey(new Object());

        assertEquals("", key);
    }

    @Test
    void testRefreshAchievementSearchCacheForUser_exception() throws Exception {

        // Valid token
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        when(cbServerProperties.isRequireEs()).thenReturn(true);

        // Cassandra insert success
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(cassandraResponse);

        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{}");

        when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        // Force ES failure during refresh cache
        when(esClientService.searchDocuments(any(), any()))
                .thenThrow(new RuntimeException("ES failure"));

        assertThrows(RuntimeException.class, () ->
                achievementService.createLearnerAchievement(
                        buildValidRequest(),
                        "token",
                        "org"
                )
        );
    }

    @Test
    void testFetchUserDetails_cassandraFallback() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.USER_ID, "user1");
        searchResult.setData(Collections.singletonList(data));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        when(cacheService.hget(any())).thenReturn(Collections.emptyList());
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
    }


    private Map<String, Object> buildValidRequest() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        return request;
    }

    @Test
    void testStatusUpdate_invalidStatusValue() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> req = new HashMap<>();
        req.put("id", "1");
        req.put("contextType", "testContext");
        req.put("learnerId", "user123");
        req.put(Constants.STATUS, "WRONG_STATUS");
        req.put("reason", "test");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, req);

        ApiResponse response =
                achievementService.statusUpdateLearnerAchievement(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetAchievementFromCassandra_contextDataString() throws Exception {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTEXT_DATA, "{\"a\":\"b\"}");
        record.put(Constants.STATUS, Constants.PENDING);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(record));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response =
                achievementService.readLearnerAchievement("1", "token", "testContext");

        assertNotNull(response);
    }

    @Test
    void testGetAndCacheAchievementFromCassandra_serializationFail() throws Exception {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTEXT_DATA, new HashMap<>());
        record.put(Constants.STATUS, Constants.PENDING);

        when(cacheService.getCache(any())).thenReturn(null);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(record));

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("Serialization fail"));

        ApiResponse response =
                achievementService.readLearnerAchievement("1", "token", "testContext");

        assertNotNull(response);
    }

    @Test
    void testUpdateAchievementInES_contextDataMap() {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.STATUS, Constants.PENDING);
        record.put("contextdata", new HashMap<>());
        record.put(Constants.CREATED_ON, LocalDate.now());

        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(record));

        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any()))
                .thenReturn(cassandraResponse);

        when(esClientService.readDocument(any(), any()))
                .thenReturn(new HashMap<>());

        Map<String, Object> req = new HashMap<>();
        req.put("id", "1");
        req.put("contextType", "testContext");
        req.put("learnerId", "user123");
        req.put(Constants.STATUS, "APPROVED");
        req.put("reason", "ok");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, req);

        achievementService.statusUpdateLearnerAchievement(request, "token");

        verify(esClientService).updateDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testRefreshAchievementSearchCacheForUser_success() throws Exception {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        when(cbServerProperties.isRequireEs()).thenReturn(true);

        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(cassandraResponse);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        SearchResult searchResult = new SearchResult();
        searchResult.setData(Collections.singletonList(new HashMap<>()));

        when(esClientService.searchDocuments(any(), any()))
                .thenReturn(searchResult);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);

        achievementService.createLearnerAchievement(
                buildValidRequest(),
                "token",
                "org"
        );

        verify(valueOperations, atLeastOnce())
                .set(anyString(), any(), anyLong(), any());
    }

    @Test
    void testFetchUserDetails_mergeBranch() throws Exception {

        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.USER_ID, "user1");
        searchResult.setData(Collections.singletonList(data));

        when(valueOperations.get(any())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any()))
                .thenReturn(searchResult);

        // Redis returns empty -> missing user
        when(cacheService.hget(any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ID, "user1");
        userMap.put(Constants.FIRST_NAME_CAMEL_CASE, "John");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(userMap));

        ApiResponse response =
                achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
    }

    // ==================== GET USER ACHIEVEMENTS TESTS ====================

    @Test
    void testGetUserAchievements_success_fromCache() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        Map<String, Object> cachedData = new HashMap<>();
        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.ID, "achv1");
        achievement.put(Constants.CONTEXT_TYPE, "testContext");
        achievement.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement);
        cachedData.put(Constants.DATA, achievements);
        cachedData.put(Constants.TOTAL_COUNT, 1);

        String cachedJson = "{\"data\":[{\"id\":\"achv1\",\"contextType\":\"testContext\"}],\"totalCount\":1}";
        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(cachedData);

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        verify(cacheService, times(1)).getCache(anyString());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testGetUserAchievements_success_fromDatabase() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievement1.put(Constants.UPDATED_ON, LocalDate.now());
        achievement1.put(Constants.FIELD_APPROVED_ON, LocalDate.now());
        achievement1.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertNotNull(searchResults.get(Constants.DATA));
        assertEquals(1, searchResults.get(Constants.TOTAL_COUNT));
        verify(cacheService, times(1)).putCache(anyString(), any());
    }

    @Test
    void testGetUserAchievements_success_withContextDataString() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, "{\"field1\":\"value1\",\"field2\":\"value2\"}");
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievement1.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        verify(objectMapper, atLeastOnce()).readValue(anyString(), eq(Map.class));
    }

    @Test
    void testGetUserAchievements_success_withLocalDateTimeFields() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, java.time.LocalDateTime.now());
        achievement1.put(Constants.UPDATED_ON, java.time.LocalDateTime.now());
        achievement1.put(Constants.FIELD_APPROVED_ON, java.time.LocalDateTime.now());
        achievement1.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        assertNotNull(resultData);
        assertEquals(1, resultData.size());
        // Verify dates are converted to strings
        assertTrue(resultData.get(0).get(Constants.CREATED_ON) instanceof String);
        assertTrue(resultData.get(0).get(Constants.UPDATED_ON) instanceof String);
        assertTrue(resultData.get(0).get(Constants.FIELD_APPROVED_ON) instanceof String);
    }

    @Test
    void testGetUserAchievements_success_emptyResults() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(0, searchResults.get(Constants.TOTAL_COUNT));
        verify(cacheService, times(1)).putCache(anyString(), any());
    }

    @Test
    void testGetUserAchievements_success_nullResults() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(null);

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(0, searchResults.get(Constants.TOTAL_COUNT));
    }

    @Test
    void testGetUserAchievements_invalidToken_blank() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Invalid or missing access token"));
        verify(cacheService, never()).getCache(anyString());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testGetUserAchievements_invalidToken_null() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(null);

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Invalid or missing access token"));
        verify(cacheService, never()).getCache(anyString());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testGetUserAchievements_cacheReadException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn("{invalid json}");
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON parse error") {});

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Failed to fetch achievements"));
    }

    @Test
    void testGetUserAchievements_databaseException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("Database connection error"));

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Failed to fetch achievements"));
    }

    @Test
    void testGetUserAchievements_contextDataParseException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, "{invalid json}");
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON parse error") {});

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        // contextData should be set to empty HashMap on parse failure
        assertTrue(resultData.get(0).get(Constants.CONTEXT_DATA) instanceof Map);
    }

    @Test
    void testGetUserAchievements_cachePutException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        doThrow(new RuntimeException("Cache write error")).when(cacheService).putCache(anyString(), any());

        ApiResponse response = achievementService.getUserAchievements("token");

        // Should still return OK even if cache write fails
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetUserAchievements_multipleAchievements_mixedDateTypes() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();

        // Achievement with LocalDate
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievement1.put(Constants.UPDATED_ON, LocalDate.now());
        achievements.add(achievement1);

        // Achievement with LocalDateTime
        Map<String, Object> achievement2 = new HashMap<>();
        achievement2.put(Constants.ID, "achv2");
        achievement2.put(Constants.CONTEXT_DATA, "{\"key\":\"value\"}");
        achievement2.put(Constants.CREATED_ON, java.time.LocalDateTime.now());
        achievement2.put(Constants.FIELD_APPROVED_ON, java.time.LocalDateTime.now());
        achievements.add(achievement2);

        // Achievement with String dates (already formatted)
        Map<String, Object> achievement3 = new HashMap<>();
        achievement3.put(Constants.ID, "achv3");
        achievement3.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement3.put(Constants.CREATED_ON, "2024-01-01");
        achievement3.put(Constants.UPDATED_ON, "2024-01-02");
        achievements.add(achievement3);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        assertEquals(3, resultData.size());
        assertEquals(3, searchResults.get(Constants.TOTAL_COUNT));

        // Verify all dates are strings
        for (Map<String, Object> achievement : resultData) {
            if (achievement.containsKey(Constants.CREATED_ON)) {
                assertTrue(achievement.get(Constants.CREATED_ON) instanceof String);
            }
            if (achievement.containsKey(Constants.UPDATED_ON)) {
                assertTrue(achievement.get(Constants.UPDATED_ON) instanceof String);
            }
            if (achievement.containsKey(Constants.FIELD_APPROVED_ON)) {
                assertTrue(achievement.get(Constants.FIELD_APPROVED_ON) instanceof String);
            }
        }
    }

    @Test
    void testGetUserAchievements_achievementWithoutDates() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.STATUS, "PENDING");
        // No date fields
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);

        ApiResponse response = achievementService.getUserAchievements("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        assertEquals(1, resultData.size());
        assertEquals("achv1", resultData.get(0).get(Constants.ID));
    }

}
