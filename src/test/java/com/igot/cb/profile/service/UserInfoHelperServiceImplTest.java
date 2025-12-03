package com.igot.cb.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProfilePreference;
import org.igot.common.cassandra.CassandraOperation;
import org.igot.common.service.OutboundRequestHandlerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoHelperServiceImplTest {

    @Mock
    private CbServerProperties serverConfig;

    @Mock
    private CacheService cacheService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private ProfileReaderServiceImpl profileReaderService;

    private UserInfoHelperServiceImpl service;

    private static final String USER_ID = "user-123";
    private static final String USER_TOKEN = "token-123";
    private static final String ROOT_ORG_ID = "org-456";

    @BeforeEach
    void setUp() {
        service = new UserInfoHelperServiceImpl(serverConfig, cacheService, cassandraOperation,
                outboundRequestHandlerService, mapper, profileReaderService);
    }

    // ==================== getUserKarmaPoints Tests ====================

    @Test
    void testGetUserKarmaPoints_WithCacheHit() {
        String redisKey = "user:karmaPoints:" + USER_ID;
        when(cacheService.getCache(redisKey)).thenReturn("150");

        int result = service.getUserKarmaPoints(USER_ID);

        assertEquals(150, result);
        verify(cacheService).getCache(redisKey);
        verify(cassandraOperation, never()).getRecordsByProperties(any(), any(), any(), any(), any());
    }

    @Test
    void testGetUserKarmaPoints_WithCacheMiss_RecordsExist() {
        String redisKey = "user:karmaPoints:" + USER_ID;
        when(cacheService.getCache(redisKey)).thenReturn(null);

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.TOTAL_POINTS, 250);
        List<Map<String, Object>> records = List.of(record);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.USER_KARMA_POINTS_SUMMARY_TABLE),
                eq(Map.of(Constants.USERID_KEY, USER_ID)),
                eq(List.of(Constants.TOTAL_POINTS)),
                isNull()
        )).thenReturn(records);

        int result = service.getUserKarmaPoints(USER_ID);

        assertEquals(250, result);
        verify(cacheService).putCache(redisKey, 250);
    }

    @Test
    void testGetUserKarmaPoints_WithException() {
        String redisKey = "user:karmaPoints:" + USER_ID;
        when(cacheService.getCache(redisKey)).thenThrow(new RuntimeException("Cache error"));

        int result = service.getUserKarmaPoints(USER_ID);

        assertEquals(0, result);
    }

    // ==================== getIssuedCertificateCount Tests ====================

    @Test
    void testGetIssuedCertificateCount_WithCacheHit() {
        String redisKey = "certificate:count";
        when(serverConfig.getCertificateCountRedisKey()).thenReturn(redisKey);
        when(serverConfig.getDataIndex()).thenReturn(1);
        when(serverConfig.getCacheTtl()).thenReturn(3600);
        when(cacheService.hget(redisKey, 1, USER_ID, 3600)).thenReturn("5");

        int result = service.getIssuedCertificateCount(USER_ID);

        assertEquals(5, result);
        verify(cassandraOperation, never()).getRecordsByProperties(any(), any(), any(), any(), any());
    }

    @Test
    void testGetIssuedCertificateCount_WithCacheMiss_MultipleSources() {
        String redisKey = "certificate:count";
        when(serverConfig.getCertificateCountRedisKey()).thenReturn(redisKey);
        when(serverConfig.getDataIndex()).thenReturn(1);
        when(serverConfig.getCacheTtl()).thenReturn(3600);
        when(serverConfig.getUserEnrolmentsTable()).thenReturn("user_enrolments");
        when(cacheService.hget(redisKey, 1, USER_ID, 3600)).thenReturn(null);

        // Course records with certificates
        Map<String, Object> courseRecord1 = new HashMap<>();
        courseRecord1.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("cert1", "cert2"));
        List<Map<String, Object>> courseRecords = List.of(courseRecord1);

        // Event records with certificates
        Map<String, Object> eventRecord = new HashMap<>();
        eventRecord.put(Constants.STATUS, 2);
        eventRecord.put(Constants.PROGRESS_KEY, 100);
        eventRecord.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("eventCert1"));
        List<Map<String, Object>> eventRecords = List.of(eventRecord);

        // External course records
        Map<String, Object> externalRecord = new HashMap<>();
        externalRecord.put(Constants.STATUS, 2);
        externalRecord.put(Constants.PROGRESS_KEY, 100);
        externalRecord.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("externalCert1"));
        List<Map<String, Object>> externalRecords = List.of(externalRecord);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq("user_enrolments"),
                eq(Map.of(Constants.USERID_KEY, USER_ID)),
                eq(List.of(Constants.ISSUED_CERTIFICATES)),
                isNull()
        )).thenReturn(courseRecords);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.USER_ENTITY_ENROLMENTS),
                any(),
                any(),
                isNull()
        )).thenReturn(eventRecords);

        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.USER_EXTERNAL_COURSE_ENROLMENTS),
                any(),
                any(),
                isNull()
        )).thenReturn(externalRecords);

        int result = service.getIssuedCertificateCount(USER_ID);

        assertEquals(3, result);  // 1 from courses + 1 from events + 1 from external
        verify(cacheService).hset(redisKey, 1, USER_ID, "3");
    }

    @Test
    void testGetIssuedCertificateCount_WithException() {
        when(serverConfig.getCertificateCountRedisKey()).thenReturn("user:certCount");
        when(cacheService.hget(anyString(), anyInt(), anyString(), anyInt())).thenThrow(new RuntimeException("Config error"));

        int result = service.getIssuedCertificateCount(USER_ID);

        assertEquals(0, result);
    }

    // ==================== getUserPostCount Tests ====================

    @Test
    void testGetUserPostCount_WithCacheHit() {
        String redisKey = "user:postCount_" + USER_ID;
        when(cacheService.getCache(redisKey)).thenReturn("10");

        int result = service.getUserPostCount(USER_ID);

        assertEquals(10, result);
        verify(outboundRequestHandlerService, never()).fetchUsingGetWithHeadersProfile(any(), any());
    }

    @Test
    void testGetUserPostCount_WithCacheMiss_ApiSuccess() {
        String redisKey = "user:postCount_" + USER_ID;
        String baseUrl = "http://community.example.com";
        String apiUrl = "/api/posts/count/";

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getCommunityBaseUrl()).thenReturn(baseUrl);
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn(apiUrl);

        Map<String, Object> apiResponse = Map.of(
            Constants.RESULT, Map.of(
                Constants.POSTCOUNT, 25
            )
        );
        when(outboundRequestHandlerService.fetchUsingGetWithHeadersProfile(
                eq(baseUrl + apiUrl + USER_ID), isNull())).thenReturn(apiResponse);

        int result = service.getUserPostCount(USER_ID);

        assertEquals(25, result);
        verify(cacheService).putCache(redisKey, 25);
    }

    @Test
    void testGetUserPostCount_WithApiException() {
        String redisKey = "user:postCount_" + USER_ID;
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getCommunityBaseUrl()).thenThrow(new RuntimeException("Config error"));

        int result = service.getUserPostCount(USER_ID);

        assertEquals(0, result);
    }

    // ==================== getUserRoles Tests ====================

    @Test
    void testGetUserRoles_WithListScope_MatchingOrg() {
        Map<String, Object> role1 = new HashMap<>();
        role1.put(Constants.ROLE, "ADMIN");
        role1.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, ROOT_ORG_ID)));

        List<Map<String, Object>> roleRecords = List.of(role1);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.USER_ROLES),
                eq(Map.of(Constants.USERID_KEY, USER_ID)),
                eq(List.of(Constants.ROLE, Constants.SCOPE)),
                isNull()
        )).thenReturn(roleRecords);

        List<String> result = service.getUserRoles(USER_ID, ROOT_ORG_ID);

        assertEquals(1, result.size());
        assertEquals("ADMIN", result.get(0));
    }

    @Test
    void testGetUserRoles_WithStringScope_ValidJson() throws Exception {
        Map<String, Object> role1 = new HashMap<>();
        role1.put(Constants.ROLE, "VIEWER");
        String scopeJson = "[{\"organisationId\":\"" + ROOT_ORG_ID + "\"}]";
        role1.put(Constants.SCOPE, scopeJson);

        List<Map<String, Object>> roleRecords = List.of(role1);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(roleRecords);
        when(mapper.readValue(eq(scopeJson), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(
                List.of(Map.of(Constants.ORGANISATION_ID, ROOT_ORG_ID))
        );

        List<String> result = service.getUserRoles(USER_ID, ROOT_ORG_ID);

        assertEquals(1, result.size());
        assertEquals("VIEWER", result.get(0));
    }

    @Test
    void testGetUserRoles_WithNonMatchingOrg() {
        Map<String, Object> role1 = new HashMap<>();
        role1.put(Constants.ROLE, "ADMIN");
        role1.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, "different-org")));

        List<Map<String, Object>> roleRecords = List.of(role1);
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(roleRecords);

        List<String> result = service.getUserRoles(USER_ID, ROOT_ORG_ID);

        assertEquals(0, result.size());
    }

    // ==================== calculateProfileCompletionPercentage Tests ====================

    @Test
    void testCalculateProfileCompletionPercentage_AllFieldsFilled() {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("firstName", "John");
        profileData.put("lastName", "Doe");
        profileData.put("email", "john@example.com");

        when(serverConfig.getProfileCompletionRequiredFields())
                .thenReturn(List.of("firstName", "lastName", "email"));
        when(serverConfig.getFieldWeight()).thenReturn(33.33);
        when(serverConfig.getExtendedFieldsConfig()).thenReturn(List.of());

        double result = service.calculateProfileCompletionPercentage(profileData, USER_ID, USER_TOKEN);

        assertEquals(99.9, result, 0.1);
    }

    @Test
    void testCalculateProfileCompletionPercentage_WithExtendedFields() {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("firstName", "John");

        when(serverConfig.getProfileCompletionRequiredFields())
                .thenReturn(List.of("firstName", "education"));
        when(serverConfig.getExtendedFieldsConfig()).thenReturn(List.of("education"));
        when(serverConfig.getFieldWeight()).thenReturn(50.0);
        when(profileReaderService.getExistingContextData(USER_ID, "education"))
                .thenReturn(List.of(Map.of("degree", "BS")));

        double result = service.calculateProfileCompletionPercentage(profileData, USER_ID, USER_TOKEN);

        assertEquals(100.0, result);
    }

    @Test
    void testCalculateProfileCompletionPercentage_NullProfile() {
        double result = service.calculateProfileCompletionPercentage(null, USER_ID, USER_TOKEN);

        assertEquals(0.0, result);
    }

    // ==================== sanitizeProfile Tests ====================

    @Test
    @SuppressWarnings("unchecked")
    void testSanitizeProfile_PublicPreference() {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFILE_PREFERENCE, 0);  // PUBLIC
        profileDetails.put(Constants.PERSONAL_DETAILS, Map.of("phone", "123-456"));
        profileDetails.put("name", "John Doe");

        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, profileDetails);

        service.sanitizeProfile(profile, USER_TOKEN);

        // For PUBLIC, everything should remain
        Map<String, Object> details = (Map<String, Object>) profile.get(Constants.PROFILE_DETAILS);
        assertTrue(details.containsKey("name"));
        assertEquals("John Doe", details.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSanitizeProfile_PrivateNoOne() {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFILE_PREFERENCE, 1);  // PRIVATE_NO_ONE
        profileDetails.put("name", "John Doe");
        profileDetails.put("email", "john@example.com");
        profileDetails.put("phone", "123-456");

        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, profileDetails);
        profile.put("phone", "123-456");

        when(serverConfig.getProfileVisibleAllowedFields()).thenReturn("name,email");
        when(serverConfig.getBasicDetailsFilteredKeys()).thenReturn("phone");

        service.sanitizeProfile(profile, USER_TOKEN);

        Map<String, Object> details = (Map<String, Object>) profile.get(Constants.PROFILE_DETAILS);
        assertTrue(details.containsKey("name"));
        assertTrue(details.containsKey("email"));
        assertFalse(profile.containsKey("phone"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSanitizeProfile_PrivateConnections_Approved() {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFILE_PREFERENCE, 10);  // PRIVATE_CONNECTIONS
        profileDetails.put("name", "John Doe");

        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, profileDetails);
        profile.put(Constants.ID, USER_ID);
        profile.put(Constants.AUTH_TOKEN, "auth-token");

        Map<String, Object> connectionResponse = Map.of(Constants.STATUS, "Approved");
        when(outboundRequestHandlerService.fetchUsingGetWithHeadersProfile(any(), any()))
                .thenReturn(Map.of(
                        Constants.RESULT, Map.of(
                                Constants.RESPONSE, connectionResponse
                        )
                ));
        when(serverConfig.getProfileVisibleAllowedFields()).thenReturn("name");
        when(serverConfig.getBasicDetailsFilteredKeys()).thenReturn("");

        service.sanitizeProfile(profile, USER_TOKEN);

        // Connection approved - should return full profile
        Map<String, Object> details = (Map<String, Object>) profile.get(Constants.PROFILE_DETAILS);
        assertTrue(details.containsKey("name"));
    }

    // ==================== checkConnected Tests ====================

    @Test
    void testCheckConnected_Success() {
        Map<String, Object> apiResponse = Map.of(
                Constants.RESULT, Map.of(
                        Constants.RESPONSE, Map.of(
                                Constants.STATUS, "Approved",
                                "connectionId", "conn-123"
                        )
                )
        );

        when(outboundRequestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), any()))
                .thenReturn(apiResponse);

        Map<String, Object> result = service.checkConnected(USER_ID, "auth-token", USER_TOKEN);

        assertNotNull(result);
        assertEquals("Approved", result.get(Constants.STATUS));
        assertEquals("conn-123", result.get("connectionId"));
    }

    @Test
    void testCheckConnected_NullResponse() {
        when(outboundRequestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), any()))
                .thenReturn(null);

        Map<String, Object> result = service.checkConnected(USER_ID, "auth-token", USER_TOKEN);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConstructor() {
        UserInfoHelperServiceImpl newService = new UserInfoHelperServiceImpl(
                serverConfig, cacheService, cassandraOperation,
                outboundRequestHandlerService, mapper, profileReaderService);

        assertNotNull(newService);
    }
}
