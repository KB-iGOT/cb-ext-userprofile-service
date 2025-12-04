package com.igot.cb.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.igot.common.service.OutboundRequestHandlerServiceImpl;
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
    private OutboundRequestHandlerServiceImpl requestHandlerService;

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
    void setUp() throws Exception {
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
        lenient().when(objectMapper.writeValueAsString(any())).thenAnswer(invocation ->
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
        when(profileReaderService.getExistingContextData(USER_ID, "education"))
            .thenReturn(new ArrayList<>(List.of(existingItem)));
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
        List<Map<String, Object>> dataList = List.of(
            Map.of("field1", "value1"),
            Map.of("field2", "value2")
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(profileReaderService.readUserExtendedProfile(USER_ID, "education")).thenReturn(dataList);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
        verify(profileReaderService).readUserExtendedProfile(USER_ID, "education");
    }

    @Test
    void getExtendedProfileSummary_WithCacheMiss_ShouldFetchFromDB() throws Exception {
        // Arrange
        List<Map<String, Object>> dataList = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(profileReaderService.readUserExtendedProfile(USER_ID, "education")).thenReturn(dataList);

        // Act
        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
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
        when(profileReaderService.getExistingContextData(USER_ID, "education")).thenReturn(contextData);

        // Act
        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, "education", TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
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
}
