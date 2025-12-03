package com.igot.cb.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.profile.service.ProfileReaderServiceImpl;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.profile.service.UserInfoHelperServiceImpl;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.ProjectUtil;
import com.igot.cb.util.UserUtility;
import org.igot.common.cassandra.CassandraOperation;
import org.igot.common.service.OutboundRequestHandlerServiceImpl;

import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplPrivateMethodTest {

    private ProfileServiceImpl profileService;

    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CbServerProperties serverConfig;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private CacheService cacheService;
    @Mock
    private ObjectMapper mapper;
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

    @BeforeEach
    void setup() {
        // Create ProfileServiceImpl with constructor injection
        profileService = new ProfileServiceImpl(
            accessTokenValidator,
            serverConfig,
            cassandraOperation,
            cacheService,
            mapper,
            projectUtil,
            requestHandlerService,
            customFieldRepository,
            esUtilService,
            userUtility,
            userInfoHelperService,
            profileReaderService
        );
    }

    @Test
    void testGetBasicProfile_InvalidToken() {
        Mockito.doAnswer(invocation -> {
            ApiResponse resp = invocation.getArgument(1);
            resp.setResponseCode(HttpStatus.UNAUTHORIZED);
            return null;
        }).when(accessTokenValidator).fetchUserIdFromAccessToken(eq("badToken"), any());

        ApiResponse response = profileService.getBasicProfile("user123", "badToken");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetBasicProfile_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache failure"));

        ApiResponse response = profileService.getBasicProfile("user123", "token123");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }
}
