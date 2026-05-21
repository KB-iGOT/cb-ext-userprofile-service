package com.igot.cb.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.UserUtility;

import org.igot.common.model.ApiResponse;
import org.igot.common.util.AccessTokenValidator;
import org.igot.common.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplPrivateMethodTest {

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CacheService cacheService;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private ObjectMapper mapper;
    @Mock
    private CbServerProperties serverConfig;
    @Mock
    private ProjectUtil projectUtil;

    @BeforeEach
    void setup() {
        // Set private fields via ReflectionTestUtils
        ReflectionTestUtils.setField(profileService, "profileVisibleAllowedFields", "name,email");
        ReflectionTestUtils.setField(profileService, "basicDetailsFilteredKeys", "password,ssn");

        // Ensure non-null ApiResponse and that errorResponse sets status codes
        Mockito.lenient().when(projectUtil.createDefaultResponse(anyString())).thenReturn(new ApiResponse());
        Mockito.lenient().doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(0);
            String msg = invocation.getArgument(1);
            HttpStatus status = invocation.getArgument(2);
            resp.setResponseCode(status);
            try {
                if (resp.getParams() != null) {
                    resp.getParams().setErrMsg(msg);
                }
            } catch (Throwable ignored) {}
            return null;
        }).when(projectUtil).errorResponse(any(ApiResponse.class), anyString(), any(HttpStatus.class));
    }

    @Test
    void testGetBasicProfile_InvalidToken() {
        Mockito.doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            resp.setResponseCode(HttpStatus.UNAUTHORIZED);
            return null;
        }).when(accessTokenValidator).fetchUserIdFromAccessToken(eq("badToken"), any(ApiResponse.class));

        ApiResponse response = profileService.getBasicProfile("user123", "badToken");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetBasicProfile_CacheHitWithDifferenceList() throws Exception {
        String userId = "user123";
        String userToken = "token123";

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn(userId);

        // Simulate cache hit with some missing fields
        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("field1", "value1");
        String cachedJson = "{\"field1\":\"value1\"}";

        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(mapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(cachedMap);

        // Server config requires more fields
        when(serverConfig.getBasicProfileFields()).thenReturn(Arrays.asList("field1", "field2"));

        // Mock DB call for missing field
        Map<String, Object> dbData = new HashMap<>(Map.of("field2", "value2"));
        ProfileServiceImpl spyService = Mockito.spy(profileService);
        doReturn(dbData).when(spyService).readUserDataFromDB(eq(userId), anyList());

        // Mock static methods
        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);

            ApiResponse response = spyService.getBasicProfile(userId, userToken);
            // Cache hit with difference list merges missing fields from DB and returns successfully
            assertEquals(HttpStatus.OK, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_NoCache_EmptyUserProfile() {
        String userId = "user123";
        String userToken = "token123";

    when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn(userId);
        when(cacheService.getCache(anyString())).thenReturn(null);

        ProfileServiceImpl spyService = Mockito.spy(profileService);
        doReturn(Collections.emptyMap()).when(spyService).readUserDataFromDB(eq(userId), isNull());

        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);
            ApiResponse response = spyService.getBasicProfile(userId, userToken);
            assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_NonSelfUser_CallsSanitize() {
        String userId = "user123";
        String userToken = "token123";

    when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("otherUser");
        when(cacheService.getCache(anyString())).thenReturn(null);

        ProfileServiceImpl spyService = Mockito.spy(profileService);
        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put("field1", "value1");

        doReturn(profileMap).when(spyService).readUserDataFromDB(eq(userId), isNull());

        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);

            ApiResponse response = spyService.getBasicProfile(userId, userToken);
            // Non-self user path calls sanitizeProfile but still returns 200 OK on success
            assertEquals(HttpStatus.OK, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_Exception() {
    when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class))).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache failure"));

        ApiResponse response = profileService.getBasicProfile("user123", "token123");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetUserBadgeCount_CacheHit_ReturnsCount() {
        String userId = "user123";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn("5");

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(5, result);
    }

    @Test
    void testGetUserBadgeCount_CacheMiss_RecordsFound_ReturnsTotalPoints() {
        String userId = "user456";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getBadgeCountRedisTtl()).thenReturn(0);

        List<Map<String, Object>> records = Arrays.asList(
                Map.of(Constants.COURSE_ID, "course1"),
                Map.of(Constants.COURSE_ID, "course2"),
                Map.of(Constants.COURSE_ID, "course3")
        );
        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD_COURSES),
                (Constants.USER_BADGE_LOOKUP_TABLE),
                (Map.of(Constants.USERID_KEY, userId)),
                (List.of(Constants.COURSE_ID)),
                (userId)
        )).thenReturn(records);

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(3, result);
        Mockito.verify(cacheService).putCache(redisKey, 3, 0);
    }

    @Test
    void testGetUserBadgeCount_CacheMiss_NoRecords_ReturnsZero() {
        String userId = "user789";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getBadgeCountRedisTtl()).thenReturn(0);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD_COURSES),
                (Constants.USER_BADGE_LOOKUP_TABLE),
                (Map.of(Constants.USERID_KEY, userId)),
                (List.of(Constants.COURSE_ID)),
                (userId)
        )).thenReturn(Collections.emptyList());

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
        Mockito.verify(cacheService).putCache(redisKey, 0, 0);
    }

    @Test
    void testGetUserBadgeCount_CacheMiss_NullRecords_ReturnsZero() {
        String userId = "userNull";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getBadgeCountRedisTtl()).thenReturn(0);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD_COURSES),
                (Constants.USER_BADGE_LOOKUP_TABLE),
                (Map.of(Constants.USERID_KEY, userId)),
                (List.of(Constants.COURSE_ID)),
                (userId)
        )).thenReturn(null);

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
        Mockito.verify(cacheService).putCache(redisKey, 0, 0);
    }

    @Test
    void testGetUserBadgeCount_CacheServiceThrowsException_ReturnsZero() {
        String userId = "userError";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenThrow(new RuntimeException("Redis unavailable"));

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
    }

    @Test
    void testGetUserBadgeCount_CassandraThrowsException_ReturnsZero() {
        String userId = "userCassandraError";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.USER_BADGE_LOOKUP_TABLE),
                any(),
                any(),
                eq(userId)
        )).thenThrow(new RuntimeException("Cassandra error"));

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
    }
}

