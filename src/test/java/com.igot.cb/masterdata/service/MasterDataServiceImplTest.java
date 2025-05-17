package com.igot.cb.masterdata.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MasterDataServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CacheService redisCacheMgr;

    @InjectMocks
    private MasterDataServiceImpl masterDataService;

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
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString());
    }

    @Test
    public void getInstitutionsList_DataFromDatabase() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.INSTITUTION_LIST)).thenReturn(null);
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> record = new HashMap<>();
        record.put("field_value", "{\"institutions\":[\"IIT\",\"NIT\"]}");
        dbResponse.add(record);
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
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString());
    }

    @Test
    public void getDegreesList_DataFromDatabase() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(redisCacheMgr.getCache(Constants.DEGREES_LIST)).thenReturn(null);
        List<Map<String, Object>> dbResponse = new ArrayList<>();
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "{\"degrees\":[\"B.Tech\",\"M.Tech\"]}");
        dbResponse.add(record);
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
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "{\"institutions\":[\"IIT\",\"NIT\"]}");
        dbResponse.add(record);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.ID, Constants.INSTITUTIONS_CONFIG);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                eq(expectedPropertiesMap),
                eq(List.of(Constants.FIELD_KEY)),
                eq(Constants.ID)))
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
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "");
        dbResponse.add(record);
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
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "{invalid-json}");
        dbResponse.add(record);
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
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "{\"degrees\":[\"B.Tech\",\"M.Tech\"]}");
        dbResponse.add(record);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.SYSTEM_SETTINGS),
                eq(expectedPropertiesMap),
                eq(List.of(Constants.FIELD_KEY)),
                eq(Constants.ID)))
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
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "");
        dbResponse.add(record);
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
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.FIELD_KEY, "{invalid-json}");
        dbResponse.add(record);
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



}