package com.igot.cb.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

@ExtendWith(MockitoExtension.class)
public class ProfileServiceImplTest {
    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CbServerProperties serverProperties;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private CacheService cacheService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProjectUtil projectUtil;

    @InjectMocks
    private ProfileServiceImpl profileService;

    private final String USER_ID = "user-123";
    private final String TOKEN = "dummy-token";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetBasicProfile_validUser_returnsProfile() throws Exception {
        Map<String, Object> dummyProfile = new HashMap<>();
        dummyProfile.put(Constants.ID, USER_ID);

        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put("name", "Test User");
        dummyProfile.put(Constants.PROFILE_DETAILS_LOWERCASE, objectMapper.writeValueAsString(profileDetailsMap));

        //when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(profileDetailsMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), anyMap(), anyList(), isNull()))
                .thenReturn(Collections.singletonList(dummyProfile));
        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(Collections.emptyList());
        //when(serverProperties.getExtendedFieldsConfig()).thenReturn(Collections.emptyList());
        //when(serverProperties.getFieldWeight()).thenReturn(10.0);

        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> responseBody = response.getResult();
        assertEquals(USER_ID, responseBody.get(Constants.ID));
    }

    @Test
    public void testGetBasicProfile_invalidToken_returnsError() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(null);

        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    public void testGetExtendedProfileSummary_noCache_fallsBackToDB() throws Exception {
        String[] contextTypes = { "education" };
        List<Map<String, Object>> dataList = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(contextTypes);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[{\"field\":\"value\"}]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(dataList);

        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    public void testSaveExtendedProfile_validInput_shouldSucceed() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("field1", "value1");
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(data));

        Map<String, Object> request = new HashMap<>(); 
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[] { "education" });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
    }

    @Test
    public void testUpdateExtendedProfile_valid_shouldSucceed() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> incoming = new HashMap<>();
        incoming.put(Constants.UUID, uuid);
        incoming.put("key", "newVal");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.UUID, uuid);
        existing.put("key", "oldVal");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(incoming));
        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[] { "education" });
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(List.of(existing));
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.updateExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
    public void testDeleteExtendedProfile_valid_shouldSucceed() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> deleteItem = Map.of(Constants.UUID, uuid);
        Map<String, Object> existingItem = Map.of(Constants.UUID, uuid, "key", "value");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(deleteItem));
        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[] { "education" });
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(new ArrayList<>(List.of(existingItem)));
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
    public void testReadFullExtendedProfile_fromCache_success() throws Exception {
        String contextType = "education";
        List<Map<String, Object>> data = List.of(Map.of("degree", "MSc"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn("[{'degree':'MSc'}]");
        when(projectUtil.parseListOfMap(anyString())).thenReturn(data);

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, contextType, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    public void testGetBasicProfile_shouldIncludeProfileCompletion() throws Exception {
        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.ID, USER_ID);
        profile.put(Constants.PROFILE_DETAILS_LOWERCASE, "{\"email\": \"test@example.com\"}");

        List<Map<String, Object>> extendedList = List.of(Map.of("org", "ABC"));
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("education", extendedList);

        ApiResponse extendedResp = ProjectUtil.createDefaultResponse("api.extendedProfile.read");
        extendedResp.setResponseCode(HttpStatus.OK);
        extendedResp.put(Constants.RESPONSE, resultMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), anyMap(), anyList(), isNull()))
                .thenReturn(List.of(profile));
        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(List.of("email", "education"));
        when(serverProperties.getExtendedFieldsConfig()).thenReturn(List.of("education"));
        when(serverProperties.getFieldWeight()).thenReturn(50.0);

        ProfileServiceImpl spyService = spy(profileService);
        doReturn(extendedResp).when(spyService).readFullExtendedProfile(USER_ID, "education", TOKEN);

        ApiResponse response = spyService.getBasicProfile(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> responseBody = response.getResult();
        assertTrue(responseBody.containsKey("profileCompletion"));
        assertEquals(50.0, responseBody.get("profileCompletion"));
    }
}
