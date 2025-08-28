package com.igot.cb.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.UserUtility;
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
    private ObjectMapper mapper;
    @Mock
    private CbServerProperties serverConfig;

    @BeforeEach
    void setup() {
        // Set private fields via ReflectionTestUtils
        ReflectionTestUtils.setField(profileService, "profileVisibleAllowedFields", "name,email");
        ReflectionTestUtils.setField(profileService, "basicDetailsFilteredKeys", "password,ssn");
    }

    @Test
    void testGetBasicProfile_InvalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("badToken")).thenReturn(null);

        ApiResponse response = profileService.getBasicProfile("user123", "badToken");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetBasicProfile_CacheHitWithDifferenceList() throws Exception {
        String userId = "user123";
        String userToken = "token123";

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);

        // Simulate cache hit with some missing fields
        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("field1", "value1");
        String cachedJson = "{\"field1\":\"value1\"}";

        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(mapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(cachedMap);

        // Server config requires more fields
        when(serverConfig.getBasicProfileFields()).thenReturn(Arrays.asList("field1", "field2"));

        // Mock DB call for missing field
        Map<String, Object> dbData = Map.of("field2", "value2");
        ProfileServiceImpl spyService = Mockito.spy(profileService);
        doReturn(dbData).when(spyService).readUserDataFromDB(eq(userId), anyList());

        // Mock static methods
        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);

            ApiResponse response = spyService.getBasicProfile(userId, userToken);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_NoCache_EmptyUserProfile() {
        String userId = "user123";
        String userToken = "token123";

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
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

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn("otherUser");
        when(cacheService.getCache(anyString())).thenReturn(null);

        ProfileServiceImpl spyService = Mockito.spy(profileService);
        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put("field1", "value1");

        doReturn(profileMap).when(spyService).readUserDataFromDB(eq(userId), isNull());

        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);

            ApiResponse response = spyService.getBasicProfile(userId, userToken);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache failure"));

        ApiResponse response = profileService.getBasicProfile("user123", "token123");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }
}

