package com.igot.cb.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.entity.CustomFieldEntity;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.profile.service.ProfileReaderServiceImpl;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.profile.service.UserInfoHelperServiceImpl;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.*;

import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test class for ProfileServiceImpl with constructor-based dependency injection.
 * This test class is designed to achieve high code coverage of the ProfileServiceImpl class.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

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
    private CustomFieldRepository customFieldRepository;

    @Mock
    private EsUtilServiceImpl esUtilService;

    @Mock
    private UserUtility userUtility;

    @Mock
    private UserInfoHelperServiceImpl userInfoHelperService;

    @Mock
    private ProfileReaderServiceImpl profileReaderService;

    private ProfileServiceImpl profileService;

    private static final String USER_ID = "user-123";
    private static final String TOKEN = "valid-token";

    @BeforeEach
    void setUp() {
        // Create ProfileServiceImpl with all mocked dependencies
        profileService = new ProfileServiceImpl(
            accessTokenValidator,
            serverProperties,
            cassandraOperation,
            cacheService,
            objectMapper,
            projectUtil,
            customFieldRepository,
            esUtilService,
            userUtility,
            userInfoHelperService,
            profileReaderService
        );

        // Set up common mock behaviors using lenient to avoid UnnecessaryStubbingException
        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenAnswer(invocation ->
                new ObjectMapper().writeValueAsString(invocation.getArgument(0))
            );
        } catch (Exception e) {
            // This should not happen in test setup
            throw new RuntimeException(e);
        }
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
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
        verify(cassandraOperation).insertRecord(any(), any(), any());
        verify(cacheService, atLeast(1)).putCache(anyString(), any());
    }

    @Test
    void saveExtendedProfile_WithInvalidToken_ShouldReturnUnauthorized() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, new HashMap<>());

        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            resp.setResponseCode(HttpStatus.UNAUTHORIZED);
            return "";
        }).when(accessTokenValidator).fetchUserIdFromAccessToken(eq(TOKEN), any());

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
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

    @Test
    void saveExtendedProfile_WithMultipleContextTypes_ShouldSucceed() {
        // Arrange
        Map<String, Object> education = new HashMap<>();
        education.put("degree", "PhD");

        Map<String, Object> service = new HashMap<>();
        service.put("position", "Manager");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(education));
        requestMap.put("experience", List.of(service));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education", "experience"});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(profileReaderService.getExistingContextData(eq(USER_ID), anyString())).thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, times(2)).insertRecord(any(), any(), any());
    }

    @Test
    void saveExtendedProfile_WithMandatoryFieldValidationError_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> education = new HashMap<>();
        education.put("degree", "");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put(Constants.EDUCATIONAL_QUALIFICATIONS, List.of(education));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institution");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void saveExtendedProfile_WithServiceHistoryCurrentlyWorking_ShouldSkipEndDateValidation() {
        // Arrange
        Map<String, Object> service = new HashMap<>();
        service.put("position", "Engineer");
        service.put("organization", "ABC Corp");
        service.put(Constants.START_DATE, "2023-01-01");
        service.put(Constants.CURRENTLY_WORKING, "true");
        // No endDate

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put(Constants.SERVICE_HISTORY, List.of(service));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.SERVICE_HISTORY});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("position,organization,startDate,endDate");
        when(profileReaderService.getExistingContextData(USER_ID, Constants.SERVICE_HISTORY)).thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void saveExtendedProfile_WithCassandraFailure_ShouldReturnInternalServerError() {
        // Arrange
        Map<String, Object> data = new HashMap<>();
        data.put("field1", "value1");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(data));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, "failed");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void saveExtendedProfile_WithEmptyContextList_ShouldSucceed() {
        // Arrange
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", new ArrayList<>());  // Empty list

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");

        // Act
        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    // ==================== updateExtendedProfile Tests ====================

    @Test
    void updateExtendedProfile_WithValidData_ShouldSucceed() {
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
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(new ArrayList<>(List.of(existing)));
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

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(profileReaderService.getExistingContextData(USER_ID, "education"))
            .thenReturn(new ArrayList<>(List.of(Map.of(Constants.UUID, UUID.randomUUID().toString()))));

        // Act
        ApiResponse response = profileService.updateExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateExtendedProfile_WithAchievements_ShouldSort() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();

        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.UUID, uuid1);
        achievement1.put(Constants.TITLE, "Updated Achievement");
        achievement1.put(Constants.ISSUED_DATE, "2024-06-01T00:00:00Z");

        Map<String, Object> existingAchievement1 = new HashMap<>();
        existingAchievement1.put(Constants.UUID, uuid1);
        existingAchievement1.put(Constants.TITLE, "Old Achievement");
        existingAchievement1.put(Constants.ISSUED_DATE, "2023-01-01T00:00:00Z");

        Map<String, Object> existingAchievement2 = new HashMap<>();
        existingAchievement2.put(Constants.UUID, uuid2);
        existingAchievement2.put(Constants.TITLE, "Another Achievement");
        existingAchievement2.put(Constants.ISSUED_DATE, "2024-01-01T00:00:00Z");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put(Constants.ACHIEVEMENTS, List.of(achievement1));

        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.ACHIEVEMENTS});
        when(profileReaderService.getExistingContextData(USER_ID, Constants.ACHIEVEMENTS))
            .thenReturn(new ArrayList<>(List.of(existingAchievement1, existingAchievement2)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.updateExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void updateExtendedProfile_WithCassandraFailure_ShouldReturnInternalServerError() {
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
        mockResponse.put(Constants.RESPONSE, "failed");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(new ArrayList<>(List.of(existing)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.updateExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== deleteExtendedProfile Tests ====================

    @Test
    void deleteExtendedProfile_WithValidUuid_ShouldSucceed() {
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
        when(profileReaderService.getExistingContextData(USER_ID, "education"))
            .thenReturn(new ArrayList<>(List.of(existingItem)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
    void deleteExtendedProfile_WithInvalidToken_ShouldReturnUnauthorized() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, new HashMap<>());

        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            resp.setResponseCode(HttpStatus.UNAUTHORIZED);
            return "";
        }).when(accessTokenValidator).fetchUserIdFromAccessToken(eq(TOKEN), any());

        // Act
        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void deleteExtendedProfile_WithUserIdMismatch_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, "different-user");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);

        // Act
        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void deleteExtendedProfile_WithCassandraFailure_ShouldReturnInternalServerError() {
        // Arrange
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> deleteItem = Map.of(Constants.UUID, uuid);
        Map<String, Object> existingItem = Map.of(Constants.UUID, uuid, "key", "value");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(deleteItem));

        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, "failed");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(profileReaderService.getExistingContextData(USER_ID, "education"))
            .thenReturn(new ArrayList<>(List.of(existingItem)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void deleteExtendedProfile_WithMultipleItems_ShouldDeleteAll() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();

        Map<String, Object> deleteItem1 = Map.of(Constants.UUID, uuid1);
        Map<String, Object> deleteItem2 = Map.of(Constants.UUID, uuid2);

        Map<String, Object> existingItem1 = Map.of(Constants.UUID, uuid1, "key", "value1");
        Map<String, Object> existingItem2 = Map.of(Constants.UUID, uuid2, "key", "value2");
        Map<String, Object> existingItem3 = Map.of(Constants.UUID, uuid3, "key", "value3");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put("education", List.of(deleteItem1, deleteItem2));

        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(profileReaderService.getExistingContextData(USER_ID, "education"))
            .thenReturn(new ArrayList<>(List.of(existingItem1, existingItem2, existingItem3)));
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.deleteExtendedProfile(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    // ==================== getExtendedProfileSummary Tests ====================

    @Test
    void getExtendedProfileSummary_WithInvalidToken_ShouldReturnBadRequest() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(null);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void getExtendedProfileSummary_WithValidData_ShouldReturnProfile() {
        // Arrange
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("education", List.of(Map.of("degree", "BS")));
        profileData.put("experience", List.of(Map.of("company", "ABC")));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(profileReaderService.readUserExtendedProfile(USER_ID)).thenReturn(profileData);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertNotNull(result);
        assertEquals(USER_ID, result.get(Constants.USERID_KEY));
    }

    @Test
    void getExtendedProfileSummary_WithEmptyProfile_ShouldReturnNoContent() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(profileReaderService.readUserExtendedProfile(USER_ID)).thenReturn(new HashMap<>());

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
    }

    // ==================== readFullExtendedProfile Tests ====================

    @Test
    void readFullExtendedProfile_WithCacheHit_ShouldReturnData() {
        // Arrange
        List<Map<String, Object>> contextData = List.of(Map.of("field", "value"));
        String cachedJson = "[{\"field\":\"value\"}]";

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        try {
            when(projectUtil.parseListOfMap(cachedJson)).thenReturn(contextData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void readFullExtendedProfile_WithCacheMiss_ShouldFetchFromDB() {
        // Arrange
        List<Map<String, Object>> contextData = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(contextData);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void readFullExtendedProfile_WithInvalidToken_ShouldReturnBadRequest() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn("");

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void readFullExtendedProfile_WithEmptyData_ShouldReturnNoContent() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(new ArrayList<>());

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
    }

    @Test
    void readFullExtendedProfile_WithNullData_ShouldReturnNoContent() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(null);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
    }

    @Test
    void readFullExtendedProfile_WithCacheException_ShouldFetchFromDB() {
        // Arrange
        List<Map<String, Object>> contextData = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache error"));
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(contextData);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void readFullExtendedProfile_WithLocationDetails_ShouldReturnFirstElement() {
        // Arrange
        List<Map<String, Object>> contextData = List.of(
            Map.of("city", "NYC", "country", "USA"),
            Map.of("city", "LA", "country", "USA")
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(profileReaderService.getExistingContextData(USER_ID, Constants.LOCATION_DETAILS)).thenReturn(contextData);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, Constants.LOCATION_DETAILS, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertNotNull(result);
        assertEquals("NYC", result.get("city"));
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
        when(profileReaderService.readUserDataFromDB(USER_ID, null)).thenReturn(userProfile);
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
        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            resp.setResponseCode(HttpStatus.UNAUTHORIZED);
            return "";
        }).when(accessTokenValidator).fetchUserIdFromAccessToken(eq(TOKEN), any());

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void getBasicProfile_WithCachedProfile_ShouldReturnCachedData() {
        // Arrange
        Map<String, Object> cachedProfile = new HashMap<>();
        cachedProfile.put(Constants.ID, USER_ID);
        cachedProfile.put("firstName", "John");
        cachedProfile.put("email", "john@example.com");
        cachedProfile.put(Constants.ROOT_ORG_ID, "org-123");

        String cachedJson = "{\"id\":\"user-123\",\"firstName\":\"John\",\"email\":\"john@example.com\",\"rootOrgId\":\"org-123\"}";

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(serverProperties.getBasicProfileFields()).thenReturn(List.of(Constants.ID, "firstName", "email", Constants.ROOT_ORG_ID));
        when(userUtility.decryptSpecificUserData(any(), any())).thenReturn(cachedProfile);

        try {
            when(objectMapper.readValue(eq(cachedJson), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cachedProfile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertNotNull(response.getResponse());
        verify(userUtility).decryptSpecificUserData(any(), any());
    }

    @Test
    void getBasicProfile_WithCacheMissingSomeFields_ShouldFetchMissingFields() {
        // Arrange
        Map<String, Object> cachedProfile = new HashMap<>();
        cachedProfile.put(Constants.ID, USER_ID);
        cachedProfile.put("firstName", "John");

        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("email", "john@example.com");
        additionalData.put(Constants.ROOT_ORG_ID, "org-123");

        String cachedJson = "{\"id\":\"user-123\",\"firstName\":\"John\"}";

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(serverProperties.getBasicProfileFields()).thenReturn(List.of(Constants.ID, "firstName", "email", Constants.ROOT_ORG_ID));
        when(profileReaderService.readUserDataFromDB(eq(USER_ID), any())).thenReturn(additionalData);
        when(userUtility.decryptSpecificUserData(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        try {
            when(objectMapper.readValue(eq(cachedJson), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cachedProfile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertNotNull(response.getResponse());
        verify(profileReaderService).readUserDataFromDB(eq(USER_ID), any());
    }

    @Test
    void getBasicProfile_WithEmptyProfile_ShouldReturnNotFound() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(profileReaderService.readUserDataFromDB(USER_ID, null)).thenReturn(new HashMap<>());
        when(userUtility.decryptSpecificUserData(any(), any())).thenReturn(new HashMap<>());

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void getBasicProfile_ForDifferentUser_ShouldSanitizeProfile() {
        // Arrange
        String differentUserId = "different-user-123";
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put(Constants.ID, differentUserId);
        userProfile.put("firstName", "Jane");
        userProfile.put("email", "jane@example.com");
        userProfile.put(Constants.ROOT_ORG_ID, "org-123");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(profileReaderService.readUserDataFromDB(differentUserId, null)).thenReturn(userProfile);
        when(userUtility.decryptSpecificUserData(any(), any())).thenReturn(userProfile);

        // Act
        ApiResponse response = profileService.getBasicProfile(differentUserId, TOKEN);

        // Assert
        assertNotNull(response.getResponse());
        verify(userInfoHelperService).sanitizeProfile(any(), eq(TOKEN));
    }

    @Test
    void getBasicProfile_WithException_ShouldReturnInternalServerError() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache failure"));

        // Act
        ApiResponse response = profileService.getBasicProfile(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== updateAdditionalFields Tests ====================

    @Test
    void updateAdditionalFields_WithInvalidToken_ShouldReturnError() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn("");

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertNotNull(response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_WithMissingUserId_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.ORGANISATION_ID, "org-123");
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of());

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_WithMissingOrganisationId_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID_RQST, USER_ID);
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of());

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_WithMissingCustomFieldValues_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID_RQST, USER_ID);
        request.put(Constants.ORGANISATION_ID, "org-123");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_WithMissingCustomFieldId_ShouldReturnBadRequest() {
        // Arrange
        Map<String, Object> customField = new HashMap<>();
        customField.put(Constants.FIELD_TYPE, "text");
        // Missing customFieldId

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID_RQST, USER_ID);
        request.put(Constants.ORGANISATION_ID, "org-123");
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of(customField));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_WithValidTextFields_ShouldSucceed() {
        // Arrange
        String customFieldId = "cf-123";
        Map<String, Object> customField = new HashMap<>();
        customField.put(Constants.CUSTOM_FIELD_ID, customFieldId);
        customField.put(Constants.FIELD_TYPE, Constants.TEXT);
        customField.put(Constants.ATTRIBUTE_NAME, "employeeId");
        customField.put(Constants.VALUE, "EMP-12345");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID_RQST, USER_ID);
        request.put(Constants.ORGANISATION_ID, "org-123");
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of(customField));

        CustomFieldEntity customFieldEntity = mock(CustomFieldEntity.class);
        JsonNode customFieldData = mock(JsonNode.class);
        JsonNode orgIdNode = mock(JsonNode.class);
        JsonNode attrNameNode = mock(JsonNode.class);
        JsonNode typeNode = mock(JsonNode.class);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId))
            .thenReturn(java.util.Optional.of(customFieldEntity));
        when(customFieldEntity.getIsActive()).thenReturn(true);
        when(customFieldEntity.getCustomFieldData()).thenReturn(customFieldData);
        when(customFieldData.get(Constants.ORGANISATION_ID)).thenReturn(orgIdNode);
        when(orgIdNode.asText()).thenReturn("org-123");
        when(customFieldData.get(Constants.ATTRIBUTE_NAME)).thenReturn(attrNameNode);
        when(attrNameNode.asText()).thenReturn("employeeId");
        when(customFieldData.get(Constants.TYPE)).thenReturn(typeNode);
        when(typeNode.asText()).thenReturn(Constants.TEXT);
        when(profileReaderService.getExistingContextData(USER_ID, Constants.ORG_ADDITIONAL_PROPERTIES))
            .thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);
        when(esUtilService.updateUserOrgCustomFields(eq(USER_ID), eq("org-123"), any())).thenReturn(true);

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation).insertRecord(any(), any(), any());
        verify(esUtilService).updateUserOrgCustomFields(eq(USER_ID), eq("org-123"), any());
    }

    @Test
    void updateAdditionalFields_WithCassandraFailure_ShouldReturnInternalServerError() {
        // Arrange
        String customFieldId = "cf-123";
        Map<String, Object> customField = new HashMap<>();
        customField.put(Constants.CUSTOM_FIELD_ID, customFieldId);
        customField.put(Constants.FIELD_TYPE, Constants.TEXT);
        customField.put(Constants.ATTRIBUTE_NAME, "employeeId");
        customField.put(Constants.VALUE, "EMP-12345");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID_RQST, USER_ID);
        request.put(Constants.USER_ID, USER_ID);
        request.put(Constants.ORGANISATION_ID, "org-123");
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of(customField));

        CustomFieldEntity customFieldEntity = mock(CustomFieldEntity.class);
        JsonNode customFieldData = mock(JsonNode.class);
        JsonNode orgIdNode = mock(JsonNode.class);
        JsonNode attrNameNode = mock(JsonNode.class);
        JsonNode typeNode = mock(JsonNode.class);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, "failed");

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId))
            .thenReturn(java.util.Optional.of(customFieldEntity));
        when(customFieldEntity.getIsActive()).thenReturn(true);
        when(customFieldEntity.getCustomFieldData()).thenReturn(customFieldData);
        when(customFieldData.get(Constants.ORGANISATION_ID)).thenReturn(orgIdNode);
        when(orgIdNode.asText()).thenReturn("org-123");
        when(customFieldData.get(Constants.ATTRIBUTE_NAME)).thenReturn(attrNameNode);
        when(attrNameNode.asText()).thenReturn("employeeId");
        when(customFieldData.get(Constants.TYPE)).thenReturn(typeNode);
        when(typeNode.asText()).thenReturn(Constants.TEXT);
        when(profileReaderService.getExistingContextData(USER_ID, Constants.ORG_ADDITIONAL_PROPERTIES))
            .thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_WithException_ShouldReturnInternalServerError() {
        // Arrange
        String customFieldId = "cf-123";
        Map<String, Object> customField = new HashMap<>();
        customField.put(Constants.CUSTOM_FIELD_ID, customFieldId);
        customField.put(Constants.FIELD_TYPE, Constants.TEXT);
        customField.put(Constants.ATTRIBUTE_NAME, "employeeId");
        customField.put(Constants.VALUE, "EMP-12345");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID_RQST, USER_ID);
        request.put(Constants.ORGANISATION_ID, "org-123");
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of(customField));

        CustomFieldEntity customFieldEntity = mock(CustomFieldEntity.class);
        JsonNode customFieldData = mock(JsonNode.class);
        JsonNode orgIdNode = mock(JsonNode.class);
        JsonNode attrNameNode = mock(JsonNode.class);
        JsonNode typeNode = mock(JsonNode.class);

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId))
            .thenReturn(java.util.Optional.of(customFieldEntity));
        when(customFieldEntity.getIsActive()).thenReturn(true);
        when(customFieldEntity.getCustomFieldData()).thenReturn(customFieldData);
        when(customFieldData.get(Constants.ORGANISATION_ID)).thenReturn(orgIdNode);
        when(orgIdNode.asText()).thenReturn("org-123");
        when(customFieldData.get(Constants.ATTRIBUTE_NAME)).thenReturn(attrNameNode);
        when(attrNameNode.asText()).thenReturn("employeeId");
        when(customFieldData.get(Constants.TYPE)).thenReturn(typeNode);
        when(typeNode.asText()).thenReturn(Constants.TEXT);
        when(profileReaderService.getExistingContextData(any(), any())).thenThrow(new RuntimeException("Database error"));

        // Act
        ApiResponse response = profileService.updateAdditionalFields(request, TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== getAdditionalFieldsByOrg Tests ====================

    @Test
    void getAdditionalFieldsByOrg_WithValidData_ShouldReturnOrgData() {
        // Arrange
        Map<String, Object> orgData1 = new HashMap<>();
        orgData1.put(Constants.ORGANISATION_ID, "org-123");
        orgData1.put(Constants.CUSTOM_FIELD_VALUES, List.of(Map.of("field", "value1")));

        Map<String, Object> orgData2 = new HashMap<>();
        orgData2.put(Constants.ORGANISATION_ID, "org-456");
        orgData2.put(Constants.CUSTOM_FIELD_VALUES, List.of(Map.of("field", "value2")));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(profileReaderService.getExistingContextData(USER_ID, Constants.ORG_ADDITIONAL_PROPERTIES))
            .thenReturn(List.of(orgData1, orgData2));

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg(USER_ID, "org-123", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("org-123", result.get(Constants.ORGANISATION_ID));
    }

    @Test
    void getAdditionalFieldsByOrg_WithInvalidToken_ShouldReturnError() {
        // Arrange
        doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            resp.setResponseCode(HttpStatus.UNAUTHORIZED);
            return "";
        }).when(accessTokenValidator).fetchUserIdFromAccessToken(eq(TOKEN), any());

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg(USER_ID, "org-123", TOKEN);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void getAdditionalFieldsByOrg_WithUserIdMismatch_ShouldReturnUnauthorized() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn("different-user");

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg(USER_ID, "org-123", TOKEN);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void getAdditionalFieldsByOrg_WithNoDataForOrg_ShouldReturnEmptyMap() {
        // Arrange
        Map<String, Object> orgData = new HashMap<>();
        orgData.put(Constants.ORGANISATION_ID, "org-456");
        orgData.put(Constants.CUSTOM_FIELD_VALUES, List.of());

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(profileReaderService.getExistingContextData(USER_ID, Constants.ORG_ADDITIONAL_PROPERTIES))
            .thenReturn(List.of(orgData));

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg(USER_ID, "org-123", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAdditionalFieldsByOrg_WithException_ShouldReturnInternalServerError() {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(profileReaderService.getExistingContextData(any(), any())).thenThrow(new RuntimeException("Database error"));

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg(USER_ID, "org-123", TOKEN);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }
}
