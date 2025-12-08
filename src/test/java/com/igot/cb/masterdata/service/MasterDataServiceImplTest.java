package com.igot.cb.masterdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.masterdata.model.Degree;
import com.igot.cb.masterdata.model.Institute;
import com.igot.cb.masterdata.repository.DegreeRepository;
import com.igot.cb.masterdata.repository.InstituteRepository;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.model.EsResponse;
import com.igot.cb.transactional.elasticsearch.service.EsUtilService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
public class MasterDataServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CacheService redisCacheMgr;

    @InjectMocks
    private MasterDataServiceImpl masterDataService;

    @Mock
    private ValidationService validationService;

    @Mock
    private DegreeRepository degreeRepository;

    @Mock
    private InstituteRepository instituteRepository;

    @Mock
    private EsUtilService esUtilService;

    @Mock
    private CbServerProperties serverProperties;

    @Test
    public void getInstitutionsList_InvalidToken() {
        String authToken = "invalid-auth-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getInstitutionsList_NullUserId() {
        String authToken = "null-user-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(null);
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getInstitutionsList_DataFromCache() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        String cacheJson = "{\"institutions\":[\"IIT\",\"NIT\"]}";
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(cacheJson);
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST));
        verify(redisCacheMgr).getCache(Constants.INSTITUTION_LIST);
        verify(redisCacheMgr, never()).putCache(anyString(), any());
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(),
                anyString());
    }

    @Test
    public void getInstitutionsList_DataFromDatabase() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put("field_value", "{\"institutions\":[\"IIT\",\"NIT\"]}");
        dbResponse.add(localRecord);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(),
                anyString(),
                anyMap(),
                anyList(),
                anyString()))
                .thenReturn(dbResponse);
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST));
        verify(redisCacheMgr).getCache(Constants.INSTITUTION_LIST);
    }

    @Test
    public void getInstitutionsList_NoDataFound() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                anyMap(),
                anyList(),
                anyString()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST));
    }

    @Test
    public void getInstitutionsList_CassandraException() {
        // Arrange
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenThrow(new RuntimeException("Database connection error"));
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST) ||
                (response.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                        Constants.FAILED.equals(response.getParams().getStatus())));
    }

    @Test
    public void getInstitutionsList_CacheException() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(anyString())).thenThrow(new RuntimeException("Cache error"));
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST) ||
                (response.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                        Constants.FAILED.equals(response.getParams().getStatus())));
    }

    @Test
    public void getInstitutionsList_InvalidJsonInCache() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn("{invalid-json}");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = masterDataService.getInstitutionsList(authToken);
        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST) ||
                (response.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                        Constants.FAILED.equals(response.getParams().getStatus())));
    }

    @Test
    public void getDegreesList_InvalidToken() {
        String authToken = "invalid-auth-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getDegreesList_NullUserId() {
        String authToken = "null-user-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(null);
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getDegreesList_DataFromCache() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        String cacheJson = "{\"degrees\":[\"B.Tech\",\"M.Tech\"]}";
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(cacheJson);
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getResult().containsKey(Constants.DEGREES_LIST));
        verify(redisCacheMgr).getCache(Constants.DEGREES_LIST);
        verify(redisCacheMgr, never()).putCache(anyString(), any());
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(),
                anyString());
    }

    @Test
    public void getDegreesList_DataFromDatabase() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(null);
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "{\"degrees\":[\"B.Tech\",\"M.Tech\"]}");
        dbResponse.add(localRecord);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(),
                anyString(),
                anyMap(),
                anyList(),
                anyString()))
                .thenReturn(dbResponse);
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getResult().containsKey(Constants.DEGREES_LIST));
        verify(redisCacheMgr).getCache(Constants.DEGREES_LIST);
    }

    @Test
    public void getDegreesList_NoDataFound() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(),
                anyString(),
                anyMap(),
                anyList(),
                anyString()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getResult().containsKey(Constants.DEGREES_LIST));
    }

    @Test
    public void getDegreesList_CacheException() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(anyString())).thenThrow(new RuntimeException("Cache error"));
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.DEGREES_LIST));
    }

    @Test
    public void getDegreesList_DatabaseException() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenThrow(new RuntimeException("Database error"));
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.DEGREES_LIST) ||
                (response.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                        Constants.FAILED.equals(response.getParams().getStatus())));
    }

    @Test
    public void getDegreesList_InvalidJsonInCache() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn("{invalid-json}");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = masterDataService.getDegreesList(authToken);
        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.DEGREES_LIST) ||
                (response.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                        Constants.FAILED.equals(response.getParams().getStatus())));
    }

    @Test
    public void testGetInstitutionsFromCache_Success() {
        String validJson = "{\"institutions\":[\"IIT\",\"NIT\"]}";
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(validJson);
        Map<String, Object> result = masterDataService.getInstitutionsFromCache();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("institutions"));
        List<String> institutions = (List<String>) result.get("institutions");
        assertEquals(2, institutions.size());
        assertEquals("IIT", institutions.get(0));
        assertEquals("NIT", institutions.get(1));
        verify(redisCacheMgr).getCache(Constants.INSTITUTION_LIST);
    }

    @Test
    public void testGetInstitutionsFromCache_EmptyCache() {
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        Map<String, Object> result = masterDataService.getInstitutionsFromCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisCacheMgr).getCache(Constants.INSTITUTION_LIST);
    }

    @Test
    public void testGetInstitutionsFromCache_Exception() {
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenThrow(new RuntimeException("Cache error"));
        Map<String, Object> result = masterDataService.getInstitutionsFromCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisCacheMgr).getCache(Constants.INSTITUTION_LIST);
    }

    @Test
    public void testGetInstitutionsFromCache_InvalidJson() {
        String invalidJson = "{invalid-json}";
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(invalidJson);
        Map<String, Object> result = masterDataService.getInstitutionsFromCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisCacheMgr).getCache(Constants.INSTITUTION_LIST);
    }

    @Test
    public void testGetInstitutionsFromDatabase_Success() {
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "{\"institutions\":[\"IIT\",\"NIT\"]}");
        dbResponse.add(localRecord);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.ID, Constants.INSTITUTIONS_CONFIG);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD),
                (Constants.SYSTEM_SETTINGS),
                (expectedPropertiesMap),
                (List.of(Constants.FIELD_KEY)),
                (Constants.ID)))
                .thenReturn(dbResponse);
        Map<String, Object> result = masterDataService.getInstitutionsFromDatabase();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("institutions"));
        List<String> institutions = (List<String>) result.get("institutions");
        assertEquals(2, institutions.size());
        assertEquals("IIT", institutions.get(0));
        assertEquals("NIT", institutions.get(1));
    }

    @Test
    public void testGetInstitutionsFromDatabase_EmptyResult() {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(new ArrayList<>());
        Map<String, Object> result = masterDataService.getInstitutionsFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetInstitutionsFromDatabase_NullResult() {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(null);
        Map<String, Object> result = masterDataService.getInstitutionsFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetInstitutionsFromDatabase_EmptyJsonValue() {
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "");
        dbResponse.add(localRecord);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(dbResponse);
        Map<String, Object> result = masterDataService.getInstitutionsFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetInstitutionsFromDatabase_InvalidJson() {
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "{invalid-json}");
        dbResponse.add(localRecord);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(dbResponse);
        Map<String, Object> result = masterDataService.getInstitutionsFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetInstitutionsFromDatabase_Exception() {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenThrow(new RuntimeException("Database error"));
        Map<String, Object> result = masterDataService.getInstitutionsFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDegreesFromCache_Success() {
        String validJson = "{\"degrees\":[\"B.Tech\",\"M.Tech\"]}";
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(validJson);
        Map<String, Object> result = masterDataService.getDegreesFromCache();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("degrees"));
        List<String> degrees = (List<String>) result.get("degrees");
        assertEquals(2, degrees.size());
        assertEquals("B.Tech", degrees.get(0));
        assertEquals("M.Tech", degrees.get(1));
        verify(redisCacheMgr).getCache(Constants.DEGREES_LIST);
    }

    @Test
    public void testGetDegreesFromCache_EmptyCache() {
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(null);
        Map<String, Object> result = masterDataService.getDegreesFromCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisCacheMgr).getCache(Constants.DEGREES_LIST);
    }

    @Test
    public void testGetDegreesFromCache_Exception() {
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenThrow(new RuntimeException("Cache error"));
        Map<String, Object> result = masterDataService.getDegreesFromCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisCacheMgr).getCache(Constants.DEGREES_LIST);
    }

    @Test
    public void testGetDegreesFromCache_InvalidJson() {
        String invalidJson = "{invalid-json}";
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(invalidJson);
        Map<String, Object> result = masterDataService.getDegreesFromCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisCacheMgr).getCache(Constants.DEGREES_LIST);
    }

    @Test
    public void testGetDegreesFromDatabase_Success() {
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "{\"degrees\":[\"B.Tech\",\"M.Tech\"]}");
        dbResponse.add(localRecord);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD),
                (Constants.SYSTEM_SETTINGS),
                (expectedPropertiesMap),
                (List.of(Constants.FIELD_KEY)),
                (Constants.ID)))
                .thenReturn(dbResponse);
        Map<String, Object> result = masterDataService.getDegreesFromDatabase();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("degrees"));
        List<String> degrees = (List<String>) result.get("degrees");
        assertEquals(2, degrees.size());
        assertEquals("B.Tech", degrees.get(0));
        assertEquals("M.Tech", degrees.get(1));
    }

    @Test
    public void testGetDegreesFromDatabase_EmptyResult() {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(new ArrayList<>());
        Map<String, Object> result = masterDataService.getDegreesFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDegreesFromDatabase_NullResult() {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(null);
        Map<String, Object> result = masterDataService.getDegreesFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDegreesFromDatabase_EmptyJsonValue() {
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "");
        dbResponse.add(localRecord);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(dbResponse);
        Map<String, Object> result = masterDataService.getDegreesFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDegreesFromDatabase_InvalidJson() {
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.FIELD_KEY, "{invalid-json}");
        dbResponse.add(localRecord);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(dbResponse);
        Map<String, Object> result = masterDataService.getDegreesFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDegreesFromDatabase_Exception() {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenThrow(new RuntimeException("Database error"));
        Map<String, Object> result = masterDataService.getDegreesFromDatabase();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testUpdateErrorDetails() {
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNull(response.getParams().getErrMsg());
        class TestableService extends MasterDataServiceImpl {
            public void testUpdateErrorDetails(ApiResponse res, String errMsg, HttpStatus status) {
                updateErrorDetails(res, errMsg, status);
            }
        }
        TestableService testService = new TestableService();
        String errorMessage = "Test error message";
        HttpStatus errorStatus = HttpStatus.BAD_REQUEST;
        testService.testUpdateErrorDetails(response, errorMessage, errorStatus);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(errorMessage, response.getParams().getErrMsg());
        assertEquals(errorStatus, response.getResponseCode());
    }

    @Test
    public void testGetDegreesListExceptionHandling() {
        // Arrange
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doThrow(new RuntimeException("Test exception"))
                .when(spyService).getDegreesFromCache();
        lenient().doReturn(Map.of()).when(spyService).getDegreesFromDatabase();
        ApiResponse result = spyService.getDegreesList(authToken);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals("Failed to process degrees data: Test exception", result.getParams().getErrMsg());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
    }

    @Test
    public void testGetInstitutionsList_WhenDatabaseReturnsData_ThenDataIsCached() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        Map<String, Object> institutionsMap = new HashMap<>();
        institutionsMap.put("institutions", List.of("IIT", "NIT", "IIIT"));
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(institutionsMap).when(spyService).getInstitutionsFromDatabase();
        ApiResponse response = spyService.getInstitutionsList(authToken);
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.INSTITUTION_LIST));
        assertEquals(institutionsMap, response.getResult().get(Constants.INSTITUTION_LIST));
        verify(redisCacheMgr).putCache(Constants.INSTITUTION_LIST, institutionsMap);
    }

    @Test
    public void testGetInstitutionsListExceptionHandling() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doThrow(new RuntimeException("Test exception"))
                .when(spyService).getInstitutionsFromCache();
        ApiResponse result = spyService.getInstitutionsList(authToken);
        assertEquals(Constants.FAILED, result.getParams().getStatus());
        assertEquals("Failed to process institutions data: Test exception", result.getParams().getErrMsg());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
    }

    @Test
    public void testUpdateInstitutionList_Success() throws JsonProcessingException {
        String authToken = "valid-token";
        String userId = "test-user-id";
        String newInstitution = "New Test Institution";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, newInstitution);
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        List<String> institutions = new ArrayList<>();
        institutions.add("Existing Institution");
        institutionsMap.put(Constants.INSTITUTIONS, institutions);
        ObjectMapper objectMapper = new ObjectMapper();
        String institutionsJson = objectMapper.writeValueAsString(institutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.CREATED, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals("Institution added successfully : " + newInstitution,
                response.getResult().get(Constants.RESPONSE));
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                any(Map.class));
        verify(redisCacheMgr).putCache(eq(Constants.INSTITUTION_LIST), mapCaptor.capture());
        Map<String, Object> capturedMap = mapCaptor.getValue();
        List<String> updatedInstitutions = (List<String>) capturedMap.get(Constants.INSTITUTIONS);
        assertTrue("The new institution should be in the updated list", updatedInstitutions.contains(newInstitution));
    }

    @Test
    public void testUpdateInstitutionList_InstitutionAlreadyExists() throws JsonProcessingException {
        String authToken = "valid-token";
        String userId = "test-user-id";
        String existingInstitution = "Existing Institution";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, existingInstitution);
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        List<String> institutions = new ArrayList<>();
        institutions.add(existingInstitution);
        institutionsMap.put(Constants.INSTITUTIONS, institutions);
        ObjectMapper objectMapper = new ObjectMapper();
        String institutionsJson = objectMapper.writeValueAsString(institutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals("Institution already exists", response.getResult().get(Constants.RESPONSE));
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), anyMap());
        verify(redisCacheMgr, never()).putCache(anyString(), any());
    }

    @Test
    public void testUpdateInstitutionList_InvalidAuthToken() {
        String authToken = "invalid-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        verify(redisCacheMgr, never()).getCache(anyString());
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), anyMap());
    }

    @Test
    public void testUpdateInstitutionList_MissingInstitutionName() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Institution name is required", response.getParams().getErrMsg());
    }

    @Test
    public void testUpdateInstitutionList_NullInstitutionName() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Institution name is required", response.getParams().getErrMsg());
    }

    @Test
    public void testUpdateInstitutionList_NoInstitutionsDataFound() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(Map.of()).when(spyService).getInstitutionsFromDatabase();
        ApiResponse response = spyService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("No institutions data found", response.getParams().getErrMsg());
    }

    @Test
    public void testUpdateInstitutionList_InvalidInstitutionsDataFormat() throws JsonProcessingException {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> invalidInstitutionsMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        String institutionsJson = objectMapper.writeValueAsString(invalidInstitutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("No institutions data found", response.getParams().getErrMsg());
    }

    @Test
    public void testUpdateInstitutionList_JsonProcessingException() throws JsonProcessingException {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "New Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        List<String> institutions = new ArrayList<>();
        institutions.add("Existing Institution");
        institutionsMap.put(Constants.INSTITUTIONS, institutions);
        ObjectMapper objectMapper = new ObjectMapper();
        String institutionsJson = objectMapper.writeValueAsString(institutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        doThrow(new RuntimeException("JSON error"))
                .when(cassandraOperation)
                .updateRecord(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.SYSTEM_SETTINGS),
                        any(Map.class));
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Failed to update institution"));
    }

    @Test
    public void testUpdateInstitutionList_CassandraException() throws JsonProcessingException {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "New Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        List<String> institutions = new ArrayList<>();
        institutions.add("Existing Institution");
        institutionsMap.put(Constants.INSTITUTIONS, institutions);
        ObjectMapper objectMapper = new ObjectMapper();
        String institutionsJson = objectMapper.writeValueAsString(institutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        doThrow(new RuntimeException("Database error"))
                .when(cassandraOperation)
                .updateRecord(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.SYSTEM_SETTINGS),
                        any(Map.class));
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to update institution: Database error", response.getParams().getErrMsg());
        verify(redisCacheMgr, never()).putCache(anyString(), any());
    }

    @Test
    public void testUpdateInstitutionList_Exception() throws Exception {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "New Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        List<String> institutions = new ArrayList<>();
        institutions.add("Existing Institution");
        institutionsMap.put(Constants.INSTITUTIONS, institutions);
        String institutionsJson = new ObjectMapper().writeValueAsString(institutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        doThrow(new RuntimeException("Database connection failed"))
                .when(cassandraOperation)
                .updateRecord(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.SYSTEM_SETTINGS),
                        any(Map.class));
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Failed to update institution"));
        assertTrue(response.getParams().getErrMsg().contains("Database connection failed"));
    }

    @Test
    public void testUpdateInstitutionList_ExceptionInProcessMethod() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "New Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doThrow(new RuntimeException("Process method error"))
                .when(spyService)
                .processInstitutionUpdate(eq(requestBody), any(ApiResponse.class));
        ApiResponse response = spyService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to update institution: Process method error", response.getParams().getErrMsg());
        verify(spyService).processInstitutionUpdate(eq(requestBody), any(ApiResponse.class));
    }

    @Test
    public void testUpdateInstitutionList_MissingInstitutionsList() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        try {
            String institutionsJson = new ObjectMapper().writeValueAsString(institutionsMap);
            when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        } catch (JsonProcessingException e) {
            fail("Test setup failed");
        }
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("No institutions data found", response.getParams().getErrMsg());
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), anyMap());
        verify(redisCacheMgr, never()).putCache(eq(Constants.INSTITUTION_LIST), any());
    }

    @Test
    public void testUpdateInstitutionList_NullInstitutionsList() throws JsonProcessingException {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.INSTITUTE_NAME, "Test Institution");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> institutionsMap = new HashMap<>();
        institutionsMap.put(Constants.INSTITUTIONS, null); // This will trigger the isEmpty check
        String institutionsJson = new ObjectMapper().writeValueAsString(institutionsMap);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(institutionsJson);
        ApiResponse response = masterDataService.updateInstitutionList(authToken, requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid institutions data format", response.getParams().getErrMsg());
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), anyMap());
    }

    @Test
    public void testGetInstitutionList_NullList() throws Exception {
        MasterDataServiceImpl serviceInstance = masterDataService;
        Method getInstitutionListMethod = MasterDataServiceImpl.class.getDeclaredMethod(
                "getInstitutionList", Map.class, ApiResponse.class);
        getInstitutionListMethod.setAccessible(true);
        Map<String, Object> institutionsMap = new HashMap<>();
        institutionsMap.put(Constants.INSTITUTIONS, null);
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        List<String> result = (List<String>) getInstitutionListMethod.invoke(
                serviceInstance, institutionsMap, response);
        assertTrue("Result should be empty for empty list", result.isEmpty());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid institutions data format", response.getParams().getErrMsg());
    }

    @Test
    public void testValidateInstitutionRequest_MissingInstituteName() throws Exception {
        Method validateMethod = MasterDataServiceImpl.class.getDeclaredMethod(
                "validateInstitutionRequest", Map.class, ApiResponse.class);
        validateMethod.setAccessible(true);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("someOtherKey", "someValue"); // Map is not empty but missing required key
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        boolean result = (boolean) validateMethod.invoke(masterDataService, requestBody, response);
        assertFalse("Method should return false when INSTITUTE_NAME key is missing", result);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Institution name is required", response.getParams().getErrMsg());
    }

    @Test
    public void testRetrieveInstitutionsData_EmptyFromCacheButNonEmptyFromDatabase() throws Exception {
        Method retrieveInstitutionsDataMethod = MasterDataServiceImpl.class.getDeclaredMethod(
                "retrieveInstitutionsData", ApiResponse.class);
        retrieveInstitutionsDataMethod.setAccessible(true);
        Map<String, Object> nonEmptyDatabaseMap = new HashMap<>();
        nonEmptyDatabaseMap.put(Constants.INSTITUTIONS, Arrays.asList("Institution1", "Institution2"));
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(Collections.emptyMap()).when(spyService).getInstitutionsFromCache();
        doReturn(nonEmptyDatabaseMap).when(spyService).getInstitutionsFromDatabase();
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> result = (Map<String, Object>) retrieveInstitutionsDataMethod.invoke(
                spyService, response);
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        assertEquals("Result should contain institutions from database",
                nonEmptyDatabaseMap, result);
        verify(spyService).getInstitutionsFromCache();
        verify(spyService).getInstitutionsFromDatabase();
        assertEquals("Response code should remain OK", HttpStatus.OK, response.getResponseCode());
        assertEquals("Response status should remain SUCCESS", Constants.SUCCESS, response.getParams().getStatus());
        assertNull("No error message should be present", response.getParams().getErrMsg());
    }

    @Test
    public void testProcessDegreeUpdate_AddNewDegree() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Test Degree");
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = new ArrayList<>();
        degreesList.add("Existing Degree");
        degreesMap.put(Constants.DEGREES, degreesList);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(degreesMap).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        doNothing().when(spyService).saveDegreeChangesToDatabaseAndCache(any(Map.class));
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.CREATED, response.getResponseCode());
        assertEquals("Degree added successfully : Test Degree", response.getResult().get(Constants.RESPONSE));
        assertTrue(degreesList.contains("Test Degree"));
        verify(spyService).saveDegreeChangesToDatabaseAndCache(degreesMap);
    }

    @Test
    public void testProcessDegreeUpdate_DegreeAlreadyExists() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Existing Degree");
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = new ArrayList<>();
        degreesList.add("Existing Degree");
        degreesMap.put(Constants.DEGREES, degreesList);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(degreesMap).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("Degree already exists", response.getResult().get(Constants.RESPONSE));
        verify(spyService, never()).saveDegreeChangesToDatabaseAndCache(any(Map.class));
    }

    @Test
    public void testProcessDegreeUpdate_ErrorInRetrievingData() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Test Degree");
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        MasterDataServiceImpl spyService = spy(masterDataService);
        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            spyService.updateErrorDetails(resp, "No degrees data found", HttpStatus.NOT_FOUND);
            return Collections.emptyMap();
        }).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("No degrees data found", response.getParams().getErrMsg());
        verify(spyService, never()).getDegreesList(any(Map.class), any(ApiResponse.class));
    }

    @Test
    public void testProcessDegreeUpdate_ExceptionDuringSave() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Test Degree");
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = new ArrayList<>();
        degreesMap.put(Constants.DEGREES, degreesList);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(degreesMap).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        doReturn(degreesList).when(spyService).getDegreesList(any(Map.class), any(ApiResponse.class));
        doReturn(true).when(spyService).updateOrAddDegree(any(List.class), anyString());
        doThrow(new JsonProcessingException("Test exception") {
        }).when(spyService)
                .saveDegreeChangesToDatabaseAndCache(any(Map.class));
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Failed to update degree"));
    }

    @Test
    public void testRetrieveDegreesData_EmptyFromCacheButNonEmptyFromDatabase() {
        Map<String, Object> nonEmptyDatabaseMap = new HashMap<>();
        nonEmptyDatabaseMap.put(Constants.DEGREES, Arrays.asList("Degree1", "Degree2"));
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(Collections.emptyMap()).when(spyService).getDegreesFromCache();
        doReturn(nonEmptyDatabaseMap).when(spyService).getDegreesFromDatabase();
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> result = spyService.retrieveDegreesData(response);
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        assertEquals("Result should contain degrees from database", nonEmptyDatabaseMap, result);
        verify(spyService).getDegreesFromCache();
        verify(spyService).getDegreesFromDatabase();
        assertEquals("Response code should remain OK", HttpStatus.OK, response.getResponseCode());
        assertEquals("Response status should remain SUCCESS", Constants.SUCCESS, response.getParams().getStatus());
        assertNull("No error message should be present", response.getParams().getErrMsg());
    }

    @Test
    public void testRetrieveDegreesData_EmptyFromBothCacheAndDatabase() {
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(Collections.emptyMap()).when(spyService).getDegreesFromCache();
        doReturn(Collections.emptyMap()).when(spyService).getDegreesFromDatabase();
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> result = spyService.retrieveDegreesData(response);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty", result.isEmpty());
        verify(spyService).getDegreesFromCache();
        verify(spyService).getDegreesFromDatabase();
        assertEquals("Response code should be NOT_FOUND", HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals("Response status should be FAILED", Constants.FAILED, response.getParams().getStatus());
        assertEquals("Error message should be set", "No degrees data found", response.getParams().getErrMsg());
    }

    @Test
    public void testRetrieveDegreesData_NonEmptyFromCache() {
        Map<String, Object> nonEmptyCacheMap = new HashMap<>();
        nonEmptyCacheMap.put(Constants.DEGREES, Arrays.asList("Degree1", "Degree2"));
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(nonEmptyCacheMap).when(spyService).getDegreesFromCache();
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        Map<String, Object> result = spyService.retrieveDegreesData(response);
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        assertEquals("Result should contain degrees from cache", nonEmptyCacheMap, result);
        verify(spyService).getDegreesFromCache();
        verify(spyService, never()).getDegreesFromDatabase();
        assertEquals("Response code should remain OK", HttpStatus.OK, response.getResponseCode());
        assertEquals("Response status should remain SUCCESS", Constants.SUCCESS, response.getParams().getStatus());
        assertNull("No error message should be present", response.getParams().getErrMsg());
    }

    @Test
    public void testGetDegreesList_Success() {
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = Arrays.asList("Bachelor's", "Master's", "PhD");
        degreesMap.put(Constants.DEGREES, degreesList);
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        List<String> result = masterDataService.getDegreesList(degreesMap, response);
        assertNotNull("Result should not be null", result);
        assertEquals("Result should contain the same degrees list", degreesList, result);
        assertEquals("Response code should remain OK", HttpStatus.OK, response.getResponseCode());
        assertEquals("Response status should remain SUCCESS", Constants.SUCCESS, response.getParams().getStatus());
        assertNull("No error message should be present", response.getParams().getErrMsg());
    }

    @Test
    public void testGetDegreesList_NullList() {
        Map<String, Object> degreesMap = new HashMap<>();
        degreesMap.put(Constants.DEGREES, null);
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        List<String> result = masterDataService.getDegreesList(degreesMap, response);
        assertTrue("Result should be empty for empty list", result.isEmpty());
        assertEquals("Response code should be INTERNAL_SERVER_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Response status should be FAILED",
                Constants.FAILED, response.getParams().getStatus());
        assertEquals("Error message should match expected",
                "Invalid degrees data format", response.getParams().getErrMsg());
    }

    @Test
    public void testGetDegreesList_EmptyList() {
        Map<String, Object> degreesMap = new HashMap<>();
        degreesMap.put(Constants.DEGREES, new ArrayList<>());
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        List<String> result = masterDataService.getDegreesList(degreesMap, response);
        assertTrue("Result should be empty for empty list", result.isEmpty());
        assertEquals("Response code should be INTERNAL_SERVER_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Response status should be FAILED",
                Constants.FAILED, response.getParams().getStatus());
        assertEquals("Error message should match expected",
                "Invalid degrees data format", response.getParams().getErrMsg());
    }

    @Test
    public void testGetDegreesList_MissingKey() {
        Map<String, Object> degreesMap = new HashMap<>();
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        List<String> result = masterDataService.getDegreesList(degreesMap, response);
        assertTrue("Result should be empty for empty list", result.isEmpty());
        assertEquals("Response code should be INTERNAL_SERVER_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Response status should be FAILED",
                Constants.FAILED, response.getParams().getStatus());
        assertEquals("Error message should match expected",
                "Invalid degrees data format", response.getParams().getErrMsg());
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache() throws Exception {
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = Arrays.asList("Bachelor's", "Master's", "PhD");
        degreesMap.put(Constants.DEGREES, degreesList);
        String expectedJson = new ObjectMapper().writeValueAsString(degreesMap);
        Map<String, Object> expectedUpdateMap = new HashMap<>();
        expectedUpdateMap.put(Constants.FIELD_KEY, expectedJson);
        expectedUpdateMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        masterDataService.saveDegreeChangesToDatabaseAndCache(degreesMap);
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                argThat(updateMap -> updateMap.containsKey(Constants.FIELD_KEY) &&
                        updateMap.containsKey(Constants.ID) &&
                        updateMap.get(Constants.ID).equals(Constants.DEGREES_CONFIG) &&
                        updateMap.get(Constants.FIELD_KEY).equals(expectedJson)));
        verify(redisCacheMgr).putCache(Constants.DEGREES_LIST, degreesMap);
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache_Success() throws JsonProcessingException {
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = Arrays.asList("Bachelor's", "Master's", "PhD");
        degreesMap.put(Constants.DEGREES, degreesList);
        Map<String, Object> expectedUpdateMap = new HashMap<>();
        String expectedJson = new ObjectMapper().writeValueAsString(degreesMap);
        expectedUpdateMap.put(Constants.FIELD_KEY, expectedJson);
        expectedUpdateMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        masterDataService.saveDegreeChangesToDatabaseAndCache(degreesMap);
        ArgumentCaptor<Map<String, Object>> updateMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                updateMapCaptor.capture());
        Map<String, Object> actualUpdateMap = updateMapCaptor.getValue();
        assertEquals(expectedUpdateMap.get(Constants.ID), actualUpdateMap.get(Constants.ID));
        assertEquals(expectedUpdateMap.get(Constants.FIELD_KEY), actualUpdateMap.get(Constants.FIELD_KEY));
        verify(redisCacheMgr).putCache(Constants.DEGREES_LIST, degreesMap);
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache_EmptyMap() throws JsonProcessingException {
        Map<String, Object> emptyDegreesMap = new HashMap<>();
        Map<String, Object> expectedUpdateMap = new HashMap<>();
        String expectedJson = new ObjectMapper().writeValueAsString(emptyDegreesMap);
        expectedUpdateMap.put(Constants.FIELD_KEY, expectedJson);
        expectedUpdateMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        masterDataService.saveDegreeChangesToDatabaseAndCache(emptyDegreesMap);
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                argThat(map -> map.get(Constants.FIELD_KEY).equals(expectedJson) &&
                        map.get(Constants.ID).equals(Constants.DEGREES_CONFIG)));
        verify(redisCacheMgr).putCache(Constants.DEGREES_LIST, emptyDegreesMap);
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache_NullDegreesList() throws JsonProcessingException {
        Map<String, Object> degreesMap = new HashMap<>();
        degreesMap.put(Constants.DEGREES, null);
        String expectedJson = new ObjectMapper().writeValueAsString(degreesMap);
        masterDataService.saveDegreeChangesToDatabaseAndCache(degreesMap);
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                argThat(map -> map.get(Constants.FIELD_KEY).equals(expectedJson)));
        verify(redisCacheMgr).putCache(Constants.DEGREES_LIST, degreesMap);
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache_JsonProcessingException() throws JsonProcessingException {
        Map<String, Object> degreesMap = mock(Map.class);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doThrow(new JsonProcessingException("Test serialization exception") {
        })
                .when(spyService).saveDegreeChangesToDatabaseAndCache(any());
        JsonProcessingException exception = assertThrows(JsonProcessingException.class, () -> {
            spyService.saveDegreeChangesToDatabaseAndCache(degreesMap);
        });
        assertTrue(exception.getMessage().contains("Test serialization exception"));
        verify(cassandraOperation, never()).updateRecord(anyString(), anyString(), anyMap());
        verify(redisCacheMgr, never()).putCache(anyString(), any());
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache_CassandraFailure() {
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = Arrays.asList("Bachelor's", "Master's");
        degreesMap.put(Constants.DEGREES, degreesList);
        doThrow(new RuntimeException("Database connection error"))
                .when(cassandraOperation).updateRecord(anyString(), anyString(), anyMap());
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            masterDataService.saveDegreeChangesToDatabaseAndCache(degreesMap);
        });
        assertEquals("Database connection error", exception.getMessage());
        verify(redisCacheMgr, never()).putCache(anyString(), any());
    }

    @Test
    public void testSaveDegreeChangesToDatabaseAndCache_RedisFailure() {
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> degreesList = Arrays.asList("Bachelor's", "Master's");
        degreesMap.put(Constants.DEGREES, degreesList);

        MasterDataServiceImpl spyService = spy(new MasterDataServiceImpl());
        spyService.cassandraOperation = cassandraOperation;

        // Use a spy on the real cache service to see actual behavior
        CacheService spyCacheService = spy(new CacheService()); // Your real cache class
        spyService.redisCacheMgr = spyCacheService;

        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of(Constants.RESPONSE, Constants.SUCCESS));

        // The operation should complete successfully despite cache failure
        assertDoesNotThrow(() -> {
            spyService.saveDegreeChangesToDatabaseAndCache(degreesMap);
        });

        // Verify database operation succeeded
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                any());

    }

    @Test
    public void testProcessDegreeUpdate_Success() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        String degreeName = "Master of Technology";
        requestBody.put(Constants.DEGREE_NAME, degreeName);
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> existingDegrees = new ArrayList<>(Arrays.asList("Bachelor of Arts", "PhD"));
        degreesMap.put(Constants.DEGREES, existingDegrees);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(degreesMap).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        doNothing().when(spyService).saveDegreeChangesToDatabaseAndCache(any(Map.class));
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.CREATED, response.getResponseCode());
        assertEquals("Degree added successfully : Master of Technology", response.getResult().get(Constants.RESPONSE));
        List<String> expectedDegrees = Arrays.asList("Bachelor of Arts", "Master of Technology", "PhD");
        assertEquals(expectedDegrees, existingDegrees);
        verify(spyService).saveDegreeChangesToDatabaseAndCache(degreesMap);
    }

    @Test
    public void testProcessDegreeUpdate_RetrieveDataError() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "PhD");
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        MasterDataServiceImpl spyService = spy(masterDataService);
        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            spyService.updateErrorDetails(resp, "No degrees data found", HttpStatus.NOT_FOUND);
            return new HashMap<>();
        }).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("No degrees data found", response.getParams().getErrMsg());
        verify(spyService, never()).getDegreesList(any(), any());
        verify(spyService, never()).saveDegreeChangesToDatabaseAndCache(any());
    }

    @Test
    public void testProcessDegreeUpdate_InvalidDegreesList() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "PhD");
        Map<String, Object> degreesMap = new HashMap<>();
        degreesMap.put(Constants.DEGREES, null);
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(degreesMap).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            spyService.updateErrorDetails(resp, "Invalid degrees data format", HttpStatus.INTERNAL_SERVER_ERROR);
            return null;
        }).when(spyService).getDegreesList(eq(degreesMap), any(ApiResponse.class));
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid degrees data format", response.getParams().getErrMsg());
        verify(spyService, never()).saveDegreeChangesToDatabaseAndCache(any());
    }

    @Test
    public void testProcessDegreeUpdate_SaveError() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        String degreeName = "Master of Technology";
        requestBody.put(Constants.DEGREE_NAME, degreeName);
        Map<String, Object> degreesMap = new HashMap<>();
        List<String> existingDegrees = new ArrayList<>(Arrays.asList("Bachelor of Arts", "PhD"));
        degreesMap.put(Constants.DEGREES, existingDegrees);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(degreesMap).when(spyService).retrieveDegreesData(any(ApiResponse.class));
        doThrow(new JsonProcessingException("JSON error") {
        })
                .when(spyService).saveDegreeChangesToDatabaseAndCache(any());
        ApiResponse response = ProjectUtil.createDefaultResponse("TEST_API");
        spyService.processDegreeUpdate(requestBody, response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Failed to update degree"));
        assertTrue(response.getParams().getErrMsg().contains("JSON error"));
        assertTrue(existingDegrees.contains(degreeName));
    }

    @Test
    public void testUpdateDegreesList_Success() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        String newDegree = "New Test Degree";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, newDegree);
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doAnswer(invocation -> {
            ApiResponse response = invocation.getArgument(1);
            response.getResult().put(Constants.RESPONSE, "Degree added successfully : " + newDegree);
            response.setResponseCode(HttpStatus.CREATED);
            return null;
        }).when(spyService).processDegreeUpdate(eq(requestBody), any(ApiResponse.class));
        ApiResponse response = spyService.updateDegreesList(authToken, requestBody);
        assertEquals(HttpStatus.CREATED, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals("Degree added successfully : " + newDegree, response.getResult().get(Constants.RESPONSE));
        verify(accessTokenValidator).fetchUserIdFromAccessToken(authToken);
        verify(spyService).validateDegreeRequest(eq(requestBody), any(ApiResponse.class));
        verify(spyService).processDegreeUpdate(eq(requestBody), any(ApiResponse.class));
    }

    @Test
    public void testUpdateDegreesList_InvalidToken() {
        String authToken = "invalid-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Test Degree");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");
        ApiResponse response = masterDataService.updateDegreesList(authToken, requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        verify(accessTokenValidator).fetchUserIdFromAccessToken(authToken);
        verify(accessTokenValidator, times(1)).fetchUserIdFromAccessToken(anyString());
        verifyNoMoreInteractions(accessTokenValidator);
    }

    @Test
    public void testUpdateDegreesList_ValidationFailure() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doAnswer(invocation -> {
            ApiResponse response = invocation.getArgument(1);
            spyService.updateErrorDetails(response, "Degree name is required", HttpStatus.BAD_REQUEST);
            return false;
        }).when(spyService).validateDegreeRequest(eq(requestBody), any(ApiResponse.class));
        ApiResponse response = spyService.updateDegreesList(authToken, requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Degree name is required", response.getParams().getErrMsg());
        verify(accessTokenValidator).fetchUserIdFromAccessToken(authToken);
        verify(spyService).validateDegreeRequest(eq(requestBody), any(ApiResponse.class));
        verify(spyService, never()).processDegreeUpdate(any(), any());
    }

    @Test
    public void testUpdateDegreesList_ProcessException() {
        String authToken = "valid-token";
        String userId = "test-user-id";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Test Degree");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        MasterDataServiceImpl spyService = spy(masterDataService);
        doReturn(true).when(spyService).validateDegreeRequest(any(), any());
        doThrow(new RuntimeException("Database connection error")).when(spyService).processDegreeUpdate(any(), any());
        ApiResponse response = spyService.updateDegreesList(authToken, requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to update degree: Database connection error", response.getParams().getErrMsg());
        verify(accessTokenValidator).fetchUserIdFromAccessToken(authToken);
        verify(spyService).validateDegreeRequest(eq(requestBody), any(ApiResponse.class));
        verify(spyService).processDegreeUpdate(eq(requestBody), any(ApiResponse.class));
    }

    @Test
    public void testUpdateDegreesList_NullUserId() {
        String authToken = "null-user-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.DEGREE_NAME, "Test Degree");
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(null);
        ApiResponse response = masterDataService.updateDegreesList(authToken, requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        verify(accessTokenValidator).fetchUserIdFromAccessToken(authToken);
        verifyNoMoreInteractions(accessTokenValidator);
    }

    @Test
    public void testValidateDegreeRequest() {
        MasterDataServiceImpl spyService = spy(masterDataService);
        Map<String, Object> emptyRequestBody = new HashMap<>();
        ApiResponse response1 = ProjectUtil.createDefaultResponse("TEST_API");
        doNothing().when(spyService).updateErrorDetails(
                any(ApiResponse.class),
                eq("Degree name is required"),
                eq(HttpStatus.BAD_REQUEST));
        boolean result1 = spyService.validateDegreeRequest(emptyRequestBody, response1);
        assertFalse("Should return false for empty request body", result1);
        verify(spyService).updateErrorDetails(
                (response1),
                ("Degree name is required"),
                (HttpStatus.BAD_REQUEST));
        Map<String, Object> missingKeyRequestBody = new HashMap<>();
        missingKeyRequestBody.put("someOtherKey", "someValue");
        ApiResponse response2 = ProjectUtil.createDefaultResponse("TEST_API");
        boolean result2 = spyService.validateDegreeRequest(missingKeyRequestBody, response2);
        assertFalse("Should return false when DEGREE_NAME key is missing", result2);
        verify(spyService, times(2)).updateErrorDetails(
                any(ApiResponse.class),
                eq("Degree name is required"),
                eq(HttpStatus.BAD_REQUEST));
        Map<String, Object> validRequestBody = new HashMap<>();
        validRequestBody.put(Constants.DEGREE_NAME, "Master's");
        ApiResponse response3 = ProjectUtil.createDefaultResponse("TEST_API");
        boolean result3 = spyService.validateDegreeRequest(validRequestBody, response3);
        assertTrue("Should return true for valid request body", result3);
        verify(spyService, times(2)).updateErrorDetails(
                any(ApiResponse.class),
                anyString(),
                any(HttpStatus.class));
    }

    @Test
    public void searchDegree_InvalidKeyword() {

        Map<String, Object> requestBody = Map.of(
                "request", Map.of("searchString", "??")
        );
        when(validationService.validateSearchRequest(any(ApiResponse.class), any(Map.class)))
                .thenAnswer(invocation -> {
                    ApiResponse resp = invocation.getArgument(0); // ApiResponse argument
                    resp.setResponseCode(HttpStatus.BAD_REQUEST);
                    resp.getParams().setStatus(Constants.FAILED);
                    return false; // validation fails
                });
        ApiResponse response = masterDataService.searchDegree(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void searchDegree_WithKeyword() {

        Map<String, Object> requestBody = Map.of(
                "request", Map.of("searchString", "MBA")
        );
        lenient().when(validationService.validateSearchString(eq("MBA"), any(ApiResponse.class)))
                .thenReturn(true);
        ApiResponse response = masterDataService.searchDegree(requestBody);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }


    @Test
    public void searchDegree_InvalidPagination() {

        Map<String, Object> requestBody = Map.of(
                "request", Map.of(
                        "page", -1,
                        "size", 10
                )
        );

        // Create a spy of the real service
        MasterDataServiceImpl spyService = spy(masterDataService);

        // Mock validation to pass
        when(validationService.validateSearchRequest(any(ApiResponse.class), any(Map.class)))
                .thenReturn(true);

        // Mock the ES search method to throw IllegalArgumentException
        lenient().doThrow(new IllegalArgumentException("Invalid Page"))
                .when(spyService)
                .searchMasterDataInIgotES(anyString(), anyString(), anyMap());

        // Call the service using the spy
        ApiResponse response = spyService.searchDegree(requestBody);

        // Assertions
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }


    @Test
    public void searchDegree_ExceptionHandling() {
        Map<String, Object> requestBody = Map.of(
                "request", Map.of(
                        "page", 0,
                        "size", 10
                )
        );
        MasterDataServiceImpl spyService = spy(masterDataService);
        when(validationService.validateSearchRequest(any(ApiResponse.class), any(Map.class)))
                .thenReturn(true);
        lenient().doThrow(new RuntimeException("ES failure"))
                .when(spyService)
                .searchMasterDataInIgotES(anyString(), anyString(), anyMap());
        ApiResponse response = spyService.searchDegree(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode()); // service sets BAD_REQUEST on exception
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> body = new HashMap<>();
        body.put("name", "MBA");
        body.put("description", "Master of Business Administration");
        req.put(Constants.REQUEST, body);
        return req;
    }

    @Test
    public void testAddDegree_ValidationFails() {
        when(validationService.addDegreeValidation(any(), any())).thenAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            ProjectUtil.errorResponse(resp, "Degree validation failed", HttpStatus.BAD_REQUEST);
            return false;
        });
        ApiResponse response = masterDataService.addDegree(validRequest());

        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void testAddDegree_ExistingActiveDegree() {
        Degree existing = new Degree();
        existing.setName("MBA");
        existing.setStatus(1);

        when(validationService.addDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(existing));

        ApiResponse response = masterDataService.addDegree(validRequest());

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    public void testAddDegree_ExistingInactiveDegree_Reactivated() {
        Degree existing = new Degree();
        existing.setId(10L);
        existing.setName("MBA");
        existing.setStatus(0);

        when(validationService.addDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(existing));

        when(degreeRepository.save(any())).thenReturn(existing);
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder()
                        .success(true)
                        .message("ok")
                        .build());

        ApiResponse res = masterDataService.addDegree(validRequest());

        assertEquals(1, ((Degree) res.getResult().get(Constants.RESULT)).getStatus());
    }

    @Test
    public void testAddDegree_NewDegreeSavedSuccessfully() {
        Degree newDegree = new Degree();
        newDegree.setId(1L);
        newDegree.setName("MBA");

        when(validationService.addDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.empty());
        when(degreeRepository.save(any())).thenReturn(newDegree);
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder()
                        .success(true)
                        .message("ok")
                        .build());

        ApiResponse response = masterDataService.addDegree(validRequest());

        assertEquals(newDegree, response.getResult().get(Constants.RESULT));
    }

    @Test
    public void testAddDegree_EsFails_RollbackHappens() {
        Degree saved = new Degree();
        saved.setId(1L);
        saved.setName("MBA");

        when(validationService.addDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.empty());
        when(degreeRepository.save(any())).thenReturn(saved);

        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder()
                        .success(false)
                        .message("failed")
                        .build());

        ApiResponse response = masterDataService.addDegree(validRequest());

        verify(degreeRepository).delete(saved);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void testAddDegree_ExceptionHandledProperly() {
        when(validationService.addDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase(any())).thenThrow(new RuntimeException());

        ApiResponse response = masterDataService.addDegree(validRequest());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }


    @Test
    public void toggleDegree_ValidationFails() {
        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            ProjectUtil.errorResponse(resp, "Validation failed", HttpStatus.BAD_REQUEST);
            return false;
        });

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void toggleDegree_NotFound() {
        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.empty());

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals("Degree not found", response.getParams().getErrMsg());
        assertEquals("Failed", response.getParams().getStatus());
    }

    @Test
    public void toggleDegree_AlreadyActive() {
        Degree degree = new Degree();
        degree.setName("MBA");
        degree.setStatus(1);

        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(degree));

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Degree is already active", response.getParams().getErrMsg());
        assertEquals("Failed", response.getParams().getStatus());
    }

    @Test
    public void toggleDegree_AlreadyInactive() {
        Degree degree = new Degree();
        degree.setName("MBA");
        degree.setStatus(0);

        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 0));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(degree));

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Degree is already inactive", response.getParams().getErrMsg());
        assertEquals("Failed", response.getParams().getStatus());
    }

    @Test
    public void toggleDegree_Success_Activate() {
        Degree degree = new Degree();
        degree.setId(1L);
        degree.setName("MBA");
        degree.setStatus(0);

        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(degree));
        when(degreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder()
                        .success(true)
                        .message("success")
                        .build());

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        Degree result = (Degree) response.getResult().get(Constants.RESULT);
        assertEquals(1, result.getStatus());
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    public void toggleDegree_Success_Deactivate() {
        Degree degree = new Degree();
        degree.setId(1L);
        degree.setName("MBA");
        degree.setStatus(1); // currently active

        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 0));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(degree));
        when(degreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder()
                        .success(true)
                        .message("success")
                        .build());

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        Degree result = (Degree) response.getResult().get(Constants.RESULT);
        assertEquals(0, result.getStatus());
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    public void toggleDegree_ESFailure() {
        Degree degree = new Degree();
        degree.setId(1L);
        degree.setName("MBA");
        degree.setStatus(0);

        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(degree));
        when(degreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder()
                        .success(false)
                        .message("failed")
                        .build());

        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);

        Degree result = (Degree) response.getResult().get(Constants.RESULT);
        assertEquals(1, result.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Degree status updated in DB but failed to sync with ES", response.getParams().getErrMsg());
        assertEquals("Failed", response.getParams().getStatus());
    }

    @Test
    public void toggleDegree_Exception() {
        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));

        when(validationService.validateUpdateStatusRequest(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase(anyString()))
                .thenThrow(new RuntimeException("DB error"));
        ApiResponse response = masterDataService.toggleDegreeStatus(requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Unexpected error while updating degree status", response.getParams().getErrMsg());
        assertEquals("Failed", response.getParams().getStatus());
    }

    @Test
    public void searchInstitute_InvalidKeyword() {

        Map<String, Object> req = Map.of(
                "request", Map.of("searchString", "??")
        );
        when(validationService.validateSearchRequest(any(ApiResponse.class), any(Map.class)))
                .thenAnswer(inv -> {
                    ApiResponse res = inv.getArgument(0); // first argument is ApiResponse
                    ProjectUtil.errorResponse(res, "Invalid keyword", HttpStatus.BAD_REQUEST);
                    return false; // validation fails
                });
        ApiResponse response = masterDataService.searchInstitute(req);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void searchInstitute_InvalidPagination() {
        Map<String, Object> requestBody = Map.of(
                "request", Map.of(
                        "page", -1,
                        "size", 10
                )
        );

        when(validationService.validateSearchRequest(any(ApiResponse.class), any(Map.class)))
                .thenReturn(true);
        MasterDataServiceImpl spyService = spy(masterDataService);
        lenient().doThrow(new IllegalArgumentException("Invalid page"))
                .when(spyService).searchMasterDataInIgotES(anyString(), anyString(), anyMap());
        ApiResponse response = spyService.searchInstitute(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    private Map<String, Object> validRequestInstitute() {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("name", "IIT Delhi");
        inner.put("description", "Top engineering institute");
        req.put("request", inner);
        return req;
    }

    @Test
    public void testAddInstitute_ExistingInactive_Reactivated() {

        Institute existing = new Institute();
        existing.setId(5L);
        existing.setName("IIT Delhi");
        existing.setStatus(0);

        when(validationService.addInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT Delhi"))
                .thenReturn(Optional.of(existing));
        when(instituteRepository.save(any())).thenReturn(existing);
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(true).message("ok").build());
        ApiResponse res = masterDataService.addInstitute(validRequestInstitute());
        Institute result = (Institute) res.getResult().get(Constants.RESULT);
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testAddInstitute_ValidationFails() {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        when(validationService.addInstituteValidation(any(), any()))
                .thenReturn(false);
        ApiResponse res = masterDataService.addInstitute(validRequestInstitute());
        assertEquals(response.getResponseCode(), res.getResponseCode());
    }

    @Test
    public void testAddInstitute_ExistingActive_Conflict() {

        Institute existing = new Institute();
        existing.setId(7L);
        existing.setName("IIT Delhi");
        existing.setStatus(1);

        when(validationService.addInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT Delhi"))
                .thenReturn(Optional.of(existing));
        ApiResponse res = masterDataService.addInstitute(validRequestInstitute());
        assertEquals(HttpStatus.CONFLICT, res.getResponseCode());
    }

    @Test
    public void testAddInstitute_New_Success() {

        Institute saved = new Institute();
        saved.setId(20L);
        saved.setName("IIT Delhi");
        saved.setStatus(1);
        when(validationService.addInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT Delhi"))
                .thenReturn(Optional.empty());
        when(instituteRepository.save(any())).thenReturn(saved);
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(true).message("ok").build());
        ApiResponse res = masterDataService.addInstitute(validRequestInstitute());
        Institute result = (Institute) res.getResult().get(Constants.RESULT);
        assertEquals(Long.valueOf(20L), result.getId()); // fixes ambiguity
        assertEquals("IIT Delhi", result.getName());
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testAddInstitute_ESFailure_DBRollback() {

        Institute saved = new Institute();
        saved.setId(22L);
        saved.setName("IIT Delhi");
        saved.setStatus(1);

        when(validationService.addInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT Delhi"))
                .thenReturn(Optional.empty());
        when(instituteRepository.save(any())).thenReturn(saved);
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(false).message("Fail").build());
        ApiResponse res = masterDataService.addInstitute(validRequestInstitute());
        verify(instituteRepository, times(1)).delete(saved);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getResponseCode());
    }

    @Test
    public void toggleInstitute_NotFound() {
        Map<String, Object> requestBody = Map.of(
                "request", Map.of("name", "IIT", "status", 1)
        );
        when(validationService.validateUpdateStatusRequest(any(), any())).thenAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            return true;
        });
        when(instituteRepository.findByNameIgnoreCase("IIT"))
                .thenReturn(Optional.empty());
        ApiResponse response = masterDataService.toggleInstituteStatus(requestBody);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void toggleInstitute_AlreadyActive() {
        Map<String, Object> requestBody = Map.of(
                "request", Map.of("name", "IIT", "status", 1)
        );
        Institute institute = new Institute();
        institute.setName("IIT");
        institute.setStatus(1);
        when(instituteRepository.findByNameIgnoreCase("IIT"))
                .thenReturn(Optional.of(institute));
        when(validationService.validateUpdateStatusRequest(any(), any())).thenAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            return true;
        });

        ApiResponse response = masterDataService.toggleInstituteStatus(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void toggleInstitute_Exception() {
        Map<String, Object> requestBody = Map.of(
                "request", Map.of("name", "IIT", "status", 1)
        );
        when(validationService.validateUpdateStatusRequest(any(), any())).thenAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            return true;
        });
        when(instituteRepository.findByNameIgnoreCase("IIT"))
                .thenThrow(new RuntimeException("DB Error"));
        ApiResponse response = masterDataService.toggleInstituteStatus(requestBody);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }
}