package com.igot.cb.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.*;

import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.igot.common.service.OutboundRequestHandlerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test class for ProfileServiceImpl with constructor-based dependency injection.
 * This test class is designed to achieve high code coverage of the ProfileServiceImpl class.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTestNew {

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

    @Mock
    private OutboundRequestHandlerServiceImpl requestHandlerService;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private EsUtilServiceImpl esUtilService;

    @Mock
    private UserUtility userUtility;

    private ProfileServiceImpl profileService;

    private static final String USER_ID = "user-123";
    private static final String TOKEN = "valid-token";

    @BeforeEach
    void setUp() throws Exception {
        // Create ProfileServiceImpl with all mocked dependencies
        profileService = new ProfileServiceImpl(
            accessTokenValidator,
            serverProperties,
            cassandraOperation,
            cacheService,
            objectMapper,
            projectUtil,
            requestHandlerService,
            customFieldRepository,
            esUtilService,
            userUtility
        );

        // Set up @Value fields using ReflectionTestUtils
        ReflectionTestUtils.setField(profileService, "profileVisibleAllowedFields", "name,email");
        ReflectionTestUtils.setField(profileService, "basicDetailsFilteredKeys", "phone,address");

        // Set up common mock behaviors
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation ->
            new ObjectMapper().writeValueAsString(invocation.getArgument(0))
        );
    }

    // ==================== saveExtendedProfile Tests ====================

    @Test
    void saveExtendedProfile_WithValidInput_ShouldSucceed() {
        // Arrange
        Map<String, Object> data = new HashMap<>();
        data.put("field1", "value1");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(data));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), any()))
            .thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
        verify(cassandraOperation).insertRecord(any(), any(), any());
        verify(cacheService).putCache(anyString(), any());
    }

    @Test
    void saveExtendedProfile_WithInvalidToken_ShouldReturnUnauthorized() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, new HashMap<>());

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(null);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertNotEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(any(), any(), any());
    }

    @Test
    void saveExtendedProfile_WithUserIdMismatch_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, "different-user");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(any(), any(), any());
    }

    @Test
    void saveExtendedProfile_WithInvalidContextType_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("invalidContext", List.of(Map.of("key", "value")));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    // ==================== updateExtendedProfile Tests ====================

    @Test
    void updateExtendedProfile_WithValidData_ShouldSucceed() throws Exception {
        // Arrange
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> incoming = new HashMap<>();
        incoming.put(Constants.UUID, uuid);
        incoming.put("key", "newValue");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.UUID, uuid);
        existing.put("key", "oldValue");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(incoming));

        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), any()))
            .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(new ArrayList<>(List.of(existing)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.updateExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation).insertRecord(any(), any(), any());
    }

    @Test
    void updateExtendedProfile_WithInvalidUuid_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> incoming = new HashMap<>();
        incoming.put(Constants.UUID, "invalid-uuid");
        incoming.put("key", "value");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(incoming));

        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
            when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
            when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
            when(projectUtil.parseListOfMap(anyString())).thenReturn(new ArrayList<>());
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }

        // Act
        ApiResponse response = profileService.updateExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    // ==================== deleteExtendedProfile Tests ====================

    @Test
    void deleteExtendedProfile_WithValidUuid_ShouldSucceed() throws Exception {
        // Arrange
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> deleteItem = Map.of(Constants.UUID, uuid);
        Map<String, Object> existingItem = Map.of(Constants.UUID, uuid, "key", "value");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(deleteItem));

        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), any()))
            .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(new ArrayList<>(List.of(existingItem)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    // ==================== getExtendedProfileSummary Tests ====================

    @Test
    void getExtendedProfileSummary_WithCacheHit_ShouldReturnCachedData() throws Exception {
        // Arrange
        Map<String, Object> cachedData = new HashMap<>();
        cachedData.put("education", Map.of("count", 2));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(objectMapper.writeValueAsString(cachedData));
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(cachedData);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
        verify(cassandraOperation, never()).getRecordsByProperties(any(), any(), any(), any(), any());
    }

    @Test
    void getExtendedProfileSummary_WithCacheMiss_ShouldFetchFromDB() throws Exception {
        // Arrange
        List<Map<String, Object>> dataList = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), isNull(), isNull()))
            .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[{\"field\":\"value\"}]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(dataList);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
        verify(cassandraOperation, atLeastOnce()).getRecordsByProperties(any(), any(), any(), any(), any());
    }

    @Test
    void getExtendedProfileSummary_WithInvalidToken_ShouldReturnBadRequest() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(null);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    // ==================== readFullExtendedProfile Tests ====================

    @Test
    void readFullExtendedProfile_WithCacheHit_ShouldReturnData() throws Exception {
        // Arrange
        List<Map<String, Object>> contextData = List.of(Map.of("field", "value"));
        String cachedJson = objectMapper.writeValueAsString(contextData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(projectUtil.parseListOfMap(cachedJson)).thenReturn(contextData);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void readFullExtendedProfile_WithCacheMiss_ShouldFetchFromDB() throws Exception {
        // Arrange
        List<Map<String, Object>> contextData = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), any()))
            .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[{\"field\":\"value\"}]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(contextData);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation).getRecordsByProperties(any(), any(), any(), any(), any());
    }

    // ==================== getBasicProfile Tests ====================

    @Test
    void getBasicProfile_WithValidToken_ShouldReturnProfile() {
        // Arrange
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put(Constants.ID, USER_ID);
        userProfile.put("name", "Test User");
        userProfile.put("email", "test@example.com");
        userProfile.put(Constants.ROOT_ORG_ID, "org-123");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(serverProperties.getBasicProfileFields()).thenReturn(List.of(Constants.ID, "name", "email"));
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), any()))
            .thenReturn(List.of(userProfile));
        when(userUtility.decryptSpecificUserData(any(), any())).thenReturn(userProfile);

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertNotNull(response.getResponse());
        verify(userUtility).decryptSpecificUserData(any(), any());
    }

    @Test
    void getBasicProfile_WithInvalidToken_ShouldReturnError() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(null);

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertNotEquals(HttpStatus.OK, response.getResponseCode());
    }

    // ==================== listCompetencies Tests ====================

    @Test
    void listCompetencies_WithCacheHit_ShouldReturnCompetencies() {
        // Arrange
        Map<String, Object> competencies = new HashMap<>();
        competencies.put("competencyAreaCounts", Map.of("area1", 5));

        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
            when(cacheService.getCache(anyString())).thenReturn("{}");
            when(projectUtil.parseMap(anyString())).thenReturn(competencies);
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }

        // Act
        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void listCompetencies_WithInvalidToken_ShouldReturnError() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(null);

        // Act
        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        // Assert
        assertNotEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void listCompetencies_WithNoCompletedCourses_ShouldReturnNoContent() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getAllRecordsByProperties(any(), any(), any(), any(), anyInt()))
            .thenReturn(new ArrayList<>());

        // Act
        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
    }

    // ==================== Helper Method Tests ====================

    @Test
    void getCourseMetadataBatched_WithValidCourseIds_ShouldReturnMetadata() {
        // Arrange
        List<String> courseIds = Arrays.asList("course1", "course2");
        Map<String, String> courseDetailsMap = new HashMap<>();
        courseDetailsMap.put("course1", "{\"courseId\":\"course1\",\"name\":\"Course 1\"}");
        courseDetailsMap.put("course2", "{\"courseId\":\"course2\",\"name\":\"Course 2\"}");

        try {
            when(cacheService.getCourseMetadataAsJsonString(any())).thenReturn(courseDetailsMap);
            when(projectUtil.parseMap(anyString())).thenAnswer(invocation -> {
                String json = invocation.getArgument(0);
                Map<String, Object> result = new HashMap<>();
                if (json.contains("course1")) {
                    result.put("courseId", "course1");
                    result.put("name", "Course 1");
                } else {
                    result.put("courseId", "course2");
                    result.put("name", "Course 2");
                }
                return result;
            });
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }

        // Act
        Map<String, Map<String, Object>> result = profileService.getCourseMetadataBatched(
            courseIds, 100, Arrays.asList("courseId", "name")
        );

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("course1"));
        assertTrue(result.containsKey("course2"));
    }

    @Test
    void getUserRoles_ShouldReturnRoles() throws Exception {
        // Arrange
        Map<String, Object> userRole = new HashMap<>();
        userRole.put(Constants.ROLE, "ADMIN");
        List<Map<String, Object>> scopes = List.of(Map.of(Constants.ORGANISATION_ID, "org-123"));
        userRole.put(Constants.SCOPE, objectMapper.writeValueAsString(scopes));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
            .thenReturn(List.of(userRole));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(scopes);

        // Act
        List<String> roles = profileService.getUserRoles(USER_ID, "org-123");

        // Assert
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("ADMIN", roles.get(0));
    }
}
