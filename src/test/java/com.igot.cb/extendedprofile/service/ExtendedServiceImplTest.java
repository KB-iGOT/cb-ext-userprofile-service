package com.igot.cb.extendedprofile.service;

import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.apache.commons.collections4.MapUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExtendedServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @InjectMocks
    private ExtendedServiceImpl extendedService;

    private Map<String, Object> requestBody;

    @Before
    public void setUp() {
        requestBody = new HashMap<>();
        requestBody.put(Constants.CONTEXT_NAME, "Maharashtra");
    }

    @Test
    public void getStatesList_Success() {
        // Arrange
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        Map<String, Object> state1 = new HashMap<>();
        state1.put(Constants.CONTEXT_NAME, "Maharashtra");
        state1.put(Constants.ID, "MH");
        cassandraResponse.add(state1);
        Map<String, Object> state2 = new HashMap<>();
        state2.put(Constants.CONTEXT_NAME, "Karnataka");
        state2.put(Constants.ID, "KA");
        cassandraResponse.add(state2);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.CONTEXT_TYPE, Constants.STATE);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                eq(expectedPropertiesMap),
                eq(List.of(Constants.CONTEXT_NAME, Constants.ID)),
                eq(Constants.CONTEXT_TYPE)))
                .thenReturn(cassandraResponse);
        ApiResponse response = extendedService.getStatesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> statesList = (List<Map<String, Object>>) response.getResult().get(Constants.STATES_LIST);
        assertNotNull(statesList);
        assertEquals(2, statesList.size());
        Map<String, Object> firstState = statesList.get(0);
        assertEquals("Maharashtra", firstState.get(Constants.STATE_NAME));
        assertEquals("MH", firstState.get(Constants.STATE_ID));
        Map<String, Object> secondState = statesList.get(1);
        assertEquals("Karnataka", secondState.get(Constants.STATE_NAME));
        assertEquals("KA", secondState.get(Constants.STATE_ID));
    }

    @Test
    public void getStatesList_EmptyUserId() {
        String authToken = "invalid-auth-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");
        ApiResponse response = extendedService.getStatesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getStatesList_NullUserId() {
        String authToken = "invalid-auth-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(null);
        ApiResponse response = extendedService.getStatesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getStatesList_CassandraException() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                anyMap(),
                eq(List.of(Constants.CONTEXT_NAME, Constants.ID)),
                eq(Constants.CONTEXT_TYPE)))
                .thenThrow(new RuntimeException("Database connection error"));
        ApiResponse response = extendedService.getStatesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Internal server error"));
    }

    @Test
    public void getStatesList_EmptyResponse() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        List<Map<String, Object>> emptyResponse = new ArrayList<>();
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                anyMap(),
                eq(List.of(Constants.CONTEXT_NAME, Constants.ID)),
                eq(Constants.CONTEXT_TYPE)))
                .thenReturn(emptyResponse);
        ApiResponse response = extendedService.getStatesList(authToken);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> statesList = (List<Map<String, Object>>) response.getResult().get(Constants.STATES_LIST);
        assertNotNull(statesList);
        assertTrue(statesList.isEmpty());
    }
    @Test
    public void getDistrictsList_Success() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        Map<String, Object> district1 = new HashMap<>();
        district1.put(Constants.CONTEXT_NAME, "Maharashtra");
        district1.put(Constants.CONTEXT_DATA, "[\"Mumbai\",\"Pune\",\"Nagpur\"]");
        cassandraResponse.add(district1);
        Map<String, Object> district2 = new HashMap<>();
        district2.put(Constants.CONTEXT_NAME, "Karnataka");
        district2.put(Constants.CONTEXT_DATA, "[\"Bangalore\",\"Mysore\"]");
        cassandraResponse.add(district2);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.CONTEXT_TYPE, Constants.DISTRICT);
        expectedPropertiesMap.put(Constants.CONTEXT_NAME, "Maharashtra");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                eq(expectedPropertiesMap),
                eq(List.of(Constants.CONTEXT_NAME, Constants.CONTEXT_DATA)),
                eq(Constants.CONTEXT_TYPE)))
                .thenReturn(cassandraResponse);

      ApiResponse response = extendedService.getDistrictsList(authToken, requestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> districtsList = (List<Map<String, Object>>) response.getResult().get(Constants.DISTRICTS_LIST);
        assertNotNull(districtsList);
        assertEquals(2, districtsList.size());
        Map<String, Object> firstState = districtsList.get(0);
        assertEquals("Maharashtra", firstState.get(Constants.STATE_NAME));
        @SuppressWarnings("unchecked")
        List<String> firstStateDistricts = (List<String>) firstState.get(Constants.DISTRICTS);
        assertEquals(3, firstStateDistricts.size());
        assertTrue(firstStateDistricts.contains("Mumbai"));
        assertTrue(firstStateDistricts.contains("Pune"));
        assertTrue(firstStateDistricts.contains("Nagpur"));
        Map<String, Object> secondState = districtsList.get(1);
        assertEquals("Karnataka", secondState.get(Constants.STATE_NAME));
        @SuppressWarnings("unchecked")
        List<String> secondStateDistricts = (List<String>) secondState.get(Constants.DISTRICTS);
        assertEquals(2, secondStateDistricts.size());
        assertTrue(secondStateDistricts.contains("Bangalore"));
        assertTrue(secondStateDistricts.contains("Mysore"));
    }

    @Test
    public void getDistrictsList_EmptyUserId() {
        String authToken = "invalid-auth-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");
        ApiResponse response = extendedService.getDistrictsList(authToken, requestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getDistrictsList_NullUserId() {
        String authToken = "invalid-auth-token";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(null);
        ApiResponse response = extendedService.getDistrictsList(authToken, requestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    public void getDistrictsList_CassandraException() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                anyMap(),
                eq(List.of(Constants.CONTEXT_NAME, Constants.CONTEXT_DATA)),
                eq(Constants.CONTEXT_TYPE)))
                .thenThrow(new RuntimeException("Database connection error"));
        ApiResponse response = extendedService.getDistrictsList(authToken, requestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Internal server error"));
    }

    @Test
    public void getDistrictsList_InvalidJsonResponse() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        Map<String, Object> districtWithInvalidJson = new HashMap<>();
        districtWithInvalidJson.put(Constants.CONTEXT_NAME, "Maharashtra");
        districtWithInvalidJson.put(Constants.CONTEXT_DATA, "{invalid-json}");
        cassandraResponse.add(districtWithInvalidJson);
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.CONTEXT_TYPE, Constants.DISTRICT);
        expectedPropertiesMap.put(Constants.CONTEXT_NAME, "Maharashtra");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                eq(expectedPropertiesMap),
                eq(List.of(Constants.CONTEXT_NAME, Constants.CONTEXT_DATA)),
                eq(Constants.CONTEXT_TYPE)))
                .thenReturn(cassandraResponse);
        ApiResponse response = extendedService.getDistrictsList(authToken, requestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> districtsList = (List<Map<String, Object>>) response.getResult().get(Constants.DISTRICTS_LIST);
        assertNotNull(districtsList);
        assertEquals(1, districtsList.size());
        Map<String, Object> firstState = districtsList.get(0);
        assertEquals("Maharashtra", firstState.get(Constants.STATE_NAME));
        @SuppressWarnings("unchecked")
        List<String> districts = (List<String>) firstState.get(Constants.DISTRICTS);
        assertTrue(districts.isEmpty());
    }

    @Test
    public void getDistrictsList_EmptyResponse() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        List<Map<String, Object>> emptyResponse = new ArrayList<>();
        Map<String, Object> expectedPropertiesMap = new HashMap<>();
        expectedPropertiesMap.put(Constants.CONTEXT_TYPE, Constants.DISTRICT);
        expectedPropertiesMap.put(Constants.CONTEXT_NAME, "Maharashtra");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.MASTER_DATA),
                eq(expectedPropertiesMap),
                eq(List.of(Constants.CONTEXT_NAME, Constants.CONTEXT_DATA)),
                eq(Constants.CONTEXT_TYPE)))
                .thenReturn(emptyResponse);
        ApiResponse response = extendedService.getDistrictsList(authToken, requestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> districtsList = (List<Map<String, Object>>) response.getResult().get(Constants.DISTRICTS_LIST);
        assertNotNull(districtsList);
        assertTrue(districtsList.isEmpty());
    }

    @Test
    public void getDistrictsList_NullRequestBody() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = extendedService.getDistrictsList(authToken, null);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Context name is missing in the request", response.getParams().getErrMsg());
    }

    @Test
    public void getDistrictsList_EmptyRequestBody() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = extendedService.getDistrictsList(authToken, new HashMap<>());
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Context name is missing in the request", response.getParams().getErrMsg());
    }

    @Test
    public void getDistrictsList_MissingContextName() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> invalidRequestBody = new HashMap<>();
        invalidRequestBody.put("someOtherKey", "someValue");
        ApiResponse response = extendedService.getDistrictsList(authToken, invalidRequestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Context name is missing in the request", response.getParams().getErrMsg());
    }


    @Test
    public void testMapUtilsIsEmpty_WithNullRequestBody() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> nullRequestBody = null;
        ApiResponse response = extendedService.getDistrictsList(authToken, nullRequestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Context name is missing in the request", response.getParams().getErrMsg());
        assertTrue("MapUtils.isEmpty should return true for null", MapUtils.isEmpty(nullRequestBody));
    }

    @Test
    public void testMapUtilsIsEmpty_WithEmptyRequestBody() {
        String authToken = "valid-auth-token";
        String userId = "test-user-id";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        Map<String, Object> emptyRequestBody = new HashMap<>();
        ApiResponse response = extendedService.getDistrictsList(authToken, emptyRequestBody);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Context name is missing in the request", response.getParams().getErrMsg());
        assertTrue("MapUtils.isEmpty should return true for empty map", MapUtils.isEmpty(emptyRequestBody));
    }
}