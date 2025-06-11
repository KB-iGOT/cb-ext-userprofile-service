package com.igot.cb.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import com.igot.cb.transactional.service.RequestHandlerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private static final String CACHE_KEY = "user:competencies:user123";
    private final String [] CONTEXT_TYPE = {"contextA"};
    private static final String REDIS_KEY = "user:extendedProfile:project:user-123";
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    static class TestContext {
        String contextKey;
        String dateField;
        List<Map<String, Object>> testData;

        TestContext(String contextKey, String dateField, List<Map<String, Object>> testData) {
            this.contextKey = contextKey;
            this.dateField = dateField;
            this.testData = testData;
        }
    }

    static Stream<TestContext> contextProvider() {
        return Stream.of(
                new TestContext(
                        Constants.SERVICE_HISTORY,
                        "startDate",
                        List.of(
                                new HashMap<>(Map.of("startDate", "2019-01-01T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startDate", "2023-06-15T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startDate", "2020-09-10T00:00:00Z", "dummyField", "dummyValue"))
                        )
                ),
                new TestContext(
                        Constants.ACHIEVEMENTS,
                        "issuedDate",
                        List.of(
                                new HashMap<>(Map.of("issuedDate", "2019-01-01T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("issuedDate", "2023-06-15T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("issuedDate", "2020-09-10T00:00:00Z", "dummyField", "dummyValue"))
                        )
                ),
                new TestContext(
                        Constants.EDUCATION_QUALIFICATION,
                        "startYear",
                        List.of(
                                new HashMap<>(Map.of("startYear", "2019", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startYear", "2023", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startYear", "2020", "dummyField", "dummyValue"))
                        )
                )
        );
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

    @ParameterizedTest
    @MethodSource("contextProvider")
    public void testSaveExtendedProfile_shouldSortByDateField(TestContext testContext) throws Exception {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, USER_ID);
        requestMap.put(testContext.contextKey, testContext.testData);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[]{ testContext.contextKey });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("dummyField");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("dummyField");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("dummyField");

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());

        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        ApiResponse response = profileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
    }

    @Test
    void testListCompetencies_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(null);

        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Invalid or missing access token", response.getParams().getErrMsg());
    }

    @Test
    void testListCompetencies_noCoursesCompleted() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        lenient().when(cacheService.getCache(CACHE_KEY)).thenReturn(null);

        Map<String, Object> dbRecord = Map.of(
                Constants.ACTIVE, true,
                Constants.STATUS, 1,
                Constants.COURSE_ID, "course1"
        );
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No competencies found for user.", response.getParams().getErrMsg());
    }



    @Test
    void testGetCourseMetadataBatched_emptyOrInvalidJson() throws IOException {
        List<String> courseIds = List.of("c1");
        when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(Map.of("c1", "{}"));
        when(projectUtil.parseMap("{}")).thenReturn(null);

        Map<String, Map<String, Object>> result = profileService.getCourseMetadataBatched(courseIds, 10, List.of("a"));
        assertTrue(result.isEmpty());
    }


    @Test
    void testListCompetencies_cacheHit() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        lenient().when(cacheService.getCache(CACHE_KEY)).thenReturn("{\"dummy\":1}");

        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No competencies found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testListCompetencies_exception() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(CACHE_KEY)).thenThrow(new RuntimeException("Redis down"));

        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Internal server error while fetching competencies", response.getParams().getErrMsg());
    }

    @Test
    void testExtendedProfile_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(null);

        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    void testExtendedProfile_cacheHit() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        String cachedJson = "{\"contextA\":{\"count\":3,\"data\":[{\"a\":1},{\"b\":2},{\"c\":3}]}}";

        String redisKey = "user:extendedProfile:all:user-123"; // Correct key
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullMap = Map.of("contextA", Map.of(
                "count", 3,
                "data", List.of(
                        Map.of("a", 1),
                        Map.of("b", 2),
                        Map.of("c", 3)
                )
        ));
        when(objectMapper.readValue(cachedJson, Map.class)).thenReturn(fullMap);

        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testExtendedProfile_cacheWriteFails() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(CACHE_KEY)).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(CONTEXT_TYPE);

        String contextJson = "[{\"a\":1}]";
        List<Map<String, Object>> records = List.of(Map.of(Constants.CONTEXT_DATA, contextJson));
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(records);

        when(projectUtil.parseListOfMap(contextJson)).thenReturn(List.of(Map.of("a", 1)));
//        when(objectMapper.writeValueAsString(any())).thenThrow(new IOException("fail"));

        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testExtendedProfile_emptyData() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(CACHE_KEY)).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(CONTEXT_TYPE);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No data found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testExtendedProfile_cacheError_thenCassandraData() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(CACHE_KEY)).thenThrow(new RuntimeException("Simulated"));

        when(serverProperties.getContextType()).thenReturn(CONTEXT_TYPE);

        String contextJson = "[{\"x\":\"1\"},{\"y\":\"2\"}]";
        List<Map<String, Object>> dbRecords = List.of(Map.of(Constants.CONTEXT_DATA, contextJson));
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(dbRecords);

        List<Map<String, Object>> parsed = List.of(Map.of("x", "1"), Map.of("y", "2"));
        when(projectUtil.parseListOfMap(contextJson)).thenReturn(parsed);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = profileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testInvalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(null);

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, Arrays.toString(CONTEXT_TYPE), TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testCacheHit() throws Exception {
        String cachedJson = "[{\"data\": \"test\"}]";
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn(cachedJson);

        List<Map<String, Object>> contextList = List.of(Map.of("data", "test"));
        when(projectUtil.parseListOfMap(cachedJson)).thenReturn(contextList);

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, Arrays.toString(CONTEXT_TYPE), TOKEN);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No data found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testCacheMiss_thenFetchFromCassandra_success() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn(null);

        String json = "[{\"data\": \"test\"}]";
        Map<String, Object> cassandraRow = Map.of(Constants.CONTEXT_DATA, json);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cassandraRow));

        List<Map<String, Object>> parsedList = List.of(Map.of("data", "test"));
        when(projectUtil.parseListOfMap(json)).thenReturn(parsedList);
        when(objectMapper.writeValueAsString(parsedList)).thenReturn(json);

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, Arrays.toString(CONTEXT_TYPE), TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(parsedList.size(), ((Map<?, ?>) response.getResult().get(Constants.RESPONSE)).get(Constants.COUNT));
    }

    @Test
    void testCacheMiss_thenFetchFromCassandra_emptyResult() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, Arrays.toString(CONTEXT_TYPE), TOKEN);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testParseListOfMapException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn("[invalid_json]");
        when(projectUtil.parseListOfMap("[invalid_json]")).thenThrow(new IOException("fail"));

        // fallback to Cassandra
        String json = "[{\"data\": \"test\"}]";
        Map<String, Object> cassandraRow = Map.of(Constants.CONTEXT_DATA, json);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cassandraRow));
        when(projectUtil.parseListOfMap(json)).thenReturn(List.of(Map.of("data", "test")));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("fail"));

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, Arrays.toString(CONTEXT_TYPE), TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testLocationDetailsBranch() {
        String contextType = Constants.LOCATION_DETAILS;
        String json = "[{\"location\": \"India\"}]";

        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
        when(cacheService.getCache(any())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, json)));
        try {
            when(projectUtil.parseListOfMap(json)).thenReturn(List.of(Map.of("location", "India")));
            when(objectMapper.writeValueAsString(any())).thenReturn(json);
        } catch (Exception e) {
            fail("Should not throw exception");
        }

        ApiResponse response = profileService.readFullExtendedProfile(USER_ID, contextType, TOKEN);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().get(Constants.RESPONSE) instanceof Map);
    }

    @Test
    void testGetBasicProfile_withValidToken_andCachedProfile() throws Exception {
        String userId = "user-123";
        String token = "valid-token";
        String cachedJson = "{\"name\":\"John\"}";
        Map<String, Object> profileMap = Map.of("name", "John");

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userId);
        when(cacheService.getCache("user:basicProfile:" + userId)).thenReturn(cachedJson);
        when(objectMapper.readValue(cachedJson, Map.class)).thenReturn(new HashMap<>(profileMap));
        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(List.of());

        ApiResponse response = profileService.getBasicProfile(userId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey("name"));
    }

    @Test
    void testGetBasicProfile_withInvalidToken() {
        String userId = "user-123";
        String token = "invalid-token";

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(null);

        ApiResponse response = profileService.getBasicProfile(userId, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Invalid or missing access token", response.getParams().getErrMsg());
    }

    // Use reflection to test private methods:
    @Test
    void testBuildCacheKey() throws Exception {
        Method method = ProfileServiceImpl.class.getDeclaredMethod("buildCacheKey", String.class, String.class, String.class);
        method.setAccessible(true);
        String key = (String) method.invoke(profileService, "user", "basicProfile", "u123");
        assertEquals("user:basicProfile:u123", key);
    }

    @Test
    void testSaveExtendedProfile_invalidUserId() {
        String userToken = "token123";
        String userId = "user123";

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userId", userId);

        Map<String, Object> request = new HashMap<>();
        request.put("request", requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn("wrongUser");

        ApiResponse response = profileService.saveExtendedProfile(request, userToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }


    @Test
    void testSaveExtendedProfile_invalidUserId1() {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.USER_ID_RQST, USER_ID);
        req.put(Constants.REQUEST, inner);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("wrong-user");

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    void testSaveExtendedProfile_invalidContextType() {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.USER_ID_RQST, USER_ID);
        inner.put("invalidContext", new ArrayList<>());
        req.put(Constants.REQUEST, inner);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[] {"validContext"});

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Invalid context type"));
    }

    @Test
    void testSaveExtendedProfile_validationFails() {
        Map<String, Object> req = Map.of(Constants.REQUEST, Map.of(Constants.USER_ID_RQST, USER_ID));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[] {});

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(Constants.OK, response.getResponseCode().getReasonPhrase());
    }

    @Test
    void testSaveExtendedProfile_nullIncomingList() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, USER_ID);
        requestData.put("contextA", null);

        Map<String, Object> req = Map.of(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(USER_ID);
        when(serverProperties.getContextType()).thenReturn(new String[] {"contextA"});

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    public void testSaveExtendedProfile_Success() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> educationItem = new HashMap<>();
        educationItem.put("degree", "Masters");
        educationItem.put("institute", "Test University");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put("education", List.of(educationItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{"education"});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()
        )).thenReturn(new ArrayList<>());
        ApiResponse mockInsertResponse = new ApiResponse();
        mockInsertResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockInsertResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]"); // Fixed: use objectMapper instead of mapper
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
        List<Map<String, Object>> result = (List<Map<String, Object>>) response.get(Constants.RESULT);
        assertEquals(1, result.size());
        assertTrue(result.get(0).containsKey(Constants.UUID));
        assertEquals("Masters", result.get(0).get("degree"));
        verify(accessTokenValidator).fetchUserIdFromAccessToken(userToken);
        verify(cassandraOperation).insertRecord(anyString(), anyString(), anyMap());
        verify(cacheService, times(1)).putCache(anyString(), any());
    }

    @Test
    public void testSaveExtendedProfile_ValidationFailure_ReturnsBadRequest() {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> educationItem = new HashMap<>();
        educationItem.put("institute", "Test University");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(Constants.EDUCATIONAL_QUALIFICATIONS, List.of(educationItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertNotNull(response.getParams().getErrMsg());
        assertTrue(response.getParams().getErrMsg().contains("degree is mandatory"));
    }

    @Test
    public void testSaveExtendedProfile_EmptyIncomingList_SkipsProcessingAndReturnsSuccess() {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.emptyList());
        Map<String, Object> validItem = new HashMap<>();
        validItem.put("someField", "someValue");
        requestData.put("otherContextType", List.of(validItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{
                Constants.EDUCATIONAL_QUALIFICATIONS, "otherContextType"
        });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockInsertResponse = new ApiResponse();
        mockInsertResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockInsertResponse);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.containsKey(Constants.CONTEXT_TYPE) &&
                        map.get(Constants.CONTEXT_TYPE).equals(Constants.EDUCATIONAL_QUALIFICATIONS))
        );
        verify(cassandraOperation, atLeastOnce()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.containsKey(Constants.CONTEXT_TYPE) &&
                        map.get(Constants.CONTEXT_TYPE).equals("otherContextType"))
        );
    }

    @Test
    public void testSaveExtendedProfile_SaveContextDataFails_ReturnsError() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = Constants.EDUCATIONAL_QUALIFICATIONS;
        Map<String, Object> educationItem = new HashMap<>();
        educationItem.put("degree", "Masters");
        educationItem.put("institute", "Test University");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType, List.of(educationItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockFailureResponse = new ApiResponse();
        mockFailureResponse.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockFailureResponse);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to save data for contextType: " + contextType, response.getParams().getErrMsg());
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(contextType))
        );
    }

    @Test
    public void testSaveExtendedProfile_UserIdMismatchWithToken_ReturnsBadRequest() {
        String tokenUserId = "token-user-123";  // User ID from token
        String requestUserId = "request-user-456";  // Different user ID in request
        String userToken = "some-token";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, requestUserId);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(tokenUserId);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
        verify(accessTokenValidator).fetchUserIdFromAccessToken(userToken);
        verifyNoMoreInteractions(cassandraOperation, cacheService);
    }

    @Test
    public void testSaveExtendedProfile_NullOrEmptyList_SkipsProcessing() {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.emptyList());  // Empty list
        requestData.put(Constants.SERVICE_HISTORY, null);  // Null list
        Map<String, Object> achievementItem = new HashMap<>();
        achievementItem.put("title", "Achievement 1");
        achievementItem.put("issuer", "Issuer 1");
        requestData.put(Constants.ACHIEVEMENTS, List.of(achievementItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{
                Constants.EDUCATIONAL_QUALIFICATIONS,
                Constants.SERVICE_HISTORY,
                Constants.ACHIEVEMENTS
        });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("title,issuer");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockResponse);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(Constants.EDUCATIONAL_QUALIFICATIONS))
        );
        verify(cassandraOperation, never()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(Constants.SERVICE_HISTORY))
        );
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(Constants.ACHIEVEMENTS))
        );
    }

    @Test
    public void testUpdateExtendedProfile_FiltersOutItemsWithoutUuid() throws IOException {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        String uuid1 = "uuid-1";
        String uuid2 = "uuid-2";
        List<Map<String, Object>> existingData = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put(Constants.UUID, uuid1);
        item1.put("degree", "Bachelor's");
        existingData.add(item1);
        Map<String, Object> item2 = new HashMap<>();
        item2.put(Constants.UUID, uuid2);
        item2.put("degree", "Master's");
        existingData.add(item2);
        Map<String, Object> item3 = new HashMap<>();
        item3.put("degree", "PhD");
        existingData.add(item3);
        Map<String, Object> update = new HashMap<>();
        update.put(Constants.UUID, uuid1);
        update.put("degree", "Updated Bachelor's");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType, List.of(update));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(existingData);
        String updatedJsonData = "[{\"uuid\":\"uuid-1\",\"degree\":\"Updated Bachelor's\"},{\"uuid\":\"uuid-2\",\"degree\":\"Master's\"}]";
        when(objectMapper.writeValueAsString(any())).thenReturn(updatedJsonData);
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockResponse);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        ArgumentCaptor<Map<String, Object>> insertCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                insertCaptor.capture());
        Map<String, Object> savedData = insertCaptor.getValue();
        assertEquals(updatedJsonData, savedData.get(Constants.CONTEXT_DATA));
        String contextData = (String) savedData.get(Constants.CONTEXT_DATA);
        assertTrue(contextData.contains(uuid1));
        assertTrue(contextData.contains(uuid2));
        assertTrue(contextData.contains("Updated Bachelor's"));
        assertFalse(contextData.contains("PhD"));
    }

    @Test
    public void testUpdateExtendedProfile_InvalidUuid_ReturnsBadRequest() throws IOException {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        String nonExistentUuid = "uuid-does-not-exist";
        List<Map<String, Object>> existingData = new ArrayList<>();
        existingData.add(Map.of(
                Constants.UUID, "existing-uuid-1",
                "degree", "Bachelor's"
        ));
        existingData.add(Map.of(
                Constants.UUID, "existing-uuid-2",
                "degree", "Master's"
        ));
        Map<String, Object> updateItem = new HashMap<>();
        updateItem.put(Constants.UUID, nonExistentUuid);
        updateItem.put("degree", "PhD");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType, List.of(updateItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(existingData);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid or missing UUID in incoming data.", response.getParams().getErrMsg());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    public void testUpdateExtendedProfile_SaveContextDataFails_ReturnsError() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        String uuid = "existing-uuid-1";
        List<Map<String, Object>> existingData = new ArrayList<>();
        Map<String, Object> existingItem = new HashMap<>();
        existingItem.put(Constants.UUID, uuid);
        existingItem.put("degree", "Bachelor's");
        existingData.add(existingItem);
        Map<String, Object> updateItem = new HashMap<>();
        updateItem.put(Constants.UUID, uuid);
        updateItem.put("degree", "Updated Degree");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType, List.of(updateItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(existingData);
        ApiResponse failureResponse = new ApiResponse();
        failureResponse.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failureResponse);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to update data for contextType: " + contextType, response.getParams().getErrMsg());
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(contextType))
        );
    }

    @Test
    public void testUpdateExtendedProfile_UserIdMismatch_ReturnsBadRequest() {
        String requestUserId = "user-123";
        String tokenUserId = "different-user-456";
        String userToken = "token-for-different-user";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, requestUserId);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(tokenUserId);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
        verify(serverProperties, never()).getContextType();
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
    }


    @Test
    public void testUpdateExtendedProfile_EmptyIncomingList_SkipsProcessing() {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType1 = "education";
        String contextType2 = "workExperience";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType1, Collections.emptyList());  // Empty list
        requestData.put(contextType2, null);  // Null list
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType1, contextType2});
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    public void testUpdateExtendedProfile_NullUuid_ReturnsBadRequest() {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        Map<String, Object> updateWithNullUuid = new HashMap<>();
        updateWithNullUuid.put("degree", "Updated Bachelor's");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType, List.of(updateWithNullUuid));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid or missing UUID in incoming data.", response.getParams().getErrMsg());
        verify(cassandraOperation).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    public void testDeleteExtendedProfile_SaveContextDataFails_ReturnsError() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> deleteItem = Map.of(Constants.UUID, uuid);
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType, List.of(deleteItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString()))
                .thenReturn(new ArrayList<>(List.of(new HashMap<>(Map.of(Constants.UUID, uuid)))));
        ApiResponse failedResponse = new ApiResponse();
        failedResponse.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failedResponse);
        ApiResponse response = profileService.deleteExtendedProfile(request, userToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to delete data for contextType: " + contextType, response.getParams().getErrMsg());
    }

    @Test
    public void testGetExtendedProfileSummary_CachePutThrowsException_LogsWarning() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        List<Map<String, Object>> contextData = List.of(Map.of("field", "value"));
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[{\"field\":\"value\"}]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(
                new ArrayList<>(List.of(new HashMap<>(Map.of("field", "value"))))
        );
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        doThrow(new RuntimeException("Cache error")).when(cacheService).putCache(anyString(), anyString());
        ApiResponse response = profileService.getExtendedProfileSummary(userId, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
        verify(cacheService).putCache(anyString(), isNull());
    }

    @Test
    void testDeleteExtendedProfile_UserIdMismatch_ReturnsBadRequest() {
        String requestUserId = "user-123";
        String tokenUserId = "different-user-456";
        String userToken = "token-for-different-user";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, requestUserId);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(tokenUserId);
        ApiResponse response = profileService.deleteExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    void testDeleteExtendedProfile_ToDeleteListNullOrEmpty_SkipsProcessing() {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType1 = "education";
        String contextType2 = "workExperience";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType1, null); // null list
        requestData.put(contextType2, Collections.emptyList()); // empty list
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType1, contextType2});
        ApiResponse response = profileService.deleteExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
        verify(cacheService, never()).putCache(anyString(), any());
    }

    @Test
    void testReadFullExtendedProfile_NoContextData_ReturnsNoContent() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType = "education";
        String redisKey = "user:extendedProfile:" + contextType + ":" + userId;
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(null); // or Collections.emptyList()
        lenient().when(projectUtil.parseListOfMap(anyString())).thenReturn(Collections.emptyList());
        ApiResponse response = profileService.readFullExtendedProfile(userId, contextType, userToken);
        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No data found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testGetBasicProfile_UserProfileNull_ReturnsNotFound() throws Exception {
        String userId = "user-123";
        String token = "valid-token";
        String cacheKey = "user:basicProfile:" + userId;
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userId);
        when(cacheService.getCache(cacheKey)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(null);
        ApiResponse response = profileService.getBasicProfile(userId, token);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertNull(response.get(Constants.RESPONSE));
    }


    @Test
    void returnsValidCount() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.POSTCOUNT, 5);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, result);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(response);
        int count = ReflectionTestUtils.invokeMethod(service, "fetchPostCountFromApi", "user-1");
        assertEquals(5, count);
    }

    @Test
    void returnsZeroOnNullResponse() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(null);
        int count = ReflectionTestUtils.invokeMethod(service, "fetchPostCountFromApi", "user-2");
        assertEquals(0, count);
    }

    @Test
    void returnsZeroOnMissingResult() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        Map<String, Object> response = new HashMap<>();
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(response);

        int count = ReflectionTestUtils.invokeMethod(service, "fetchPostCountFromApi", "user-3");
        assertEquals(0, count);
    }

    @Test
    void returnsZeroOnNonIntegerPostCount() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.POSTCOUNT, "not-an-int");
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, result);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(response);
        int count = ReflectionTestUtils.invokeMethod(service, "fetchPostCountFromApi", "user-4");
        assertEquals(0, count);
    }

    @Test
    void returnsZeroOnException() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenThrow(new RuntimeException("API error"));
        int count = ReflectionTestUtils.invokeMethod(service, "fetchPostCountFromApi", "user-5");
        assertEquals(0, count);
    }

    @Test
    void testGetUserPostCount_cacheHit() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        when(cacheService.getCache("user:communityPostCount:user1")).thenReturn("10");
        int count = ReflectionTestUtils.invokeMethod(service, "getUserPostCount", "user1");
        assertEquals(10, count);
        verify(cacheService).getCache("user:communityPostCount:user1");
        verifyNoMoreInteractions(cacheService);
    }

    @Test
    void testGetUserPostCount_cacheValueNotInteger_returnsZero() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        when(cacheService.getCache("user:communityPostCount:user3")).thenReturn("not-a-number");
        int count = ReflectionTestUtils.invokeMethod(service, "getUserPostCount", "user3");
        assertEquals(0, count);
    }

    @Test
    void testGetUserPostCount_exception_returnsZero() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        when(cacheService.getCache("user:communityPostCount:user4")).thenThrow(new RuntimeException("Redis error"));
        int count = ReflectionTestUtils.invokeMethod(service, "getUserPostCount", "user4");
        assertEquals(0, count);
    }

    @Test
    void sanitizeProfile_removesPersonalDetails_whenPresent() {
        Map<String, Object> detailsMap = new HashMap<>();
        detailsMap.put(Constants.PERSONAL_DETAILS, Map.of("a", "b"));
        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, detailsMap);
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.invokeMethod(service, "sanitizeProfile", profile);
        assertFalse(detailsMap.containsKey(Constants.PERSONAL_DETAILS));
    }

    @Test
    void sanitizeProfile_doesNothing_whenPersonalDetailsNotPresent() {
        Map<String, Object> detailsMap = new HashMap<>();
        detailsMap.put("other", "value");
        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, detailsMap);
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.invokeMethod(service, "sanitizeProfile", profile);
        assertTrue(detailsMap.containsKey("other"));
    }

    @Test
    void sanitizeProfile_doesNothing_whenProfileDetailsIsNotMap() {
        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, "notAMap");
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.invokeMethod(service, "sanitizeProfile", profile);
    }

    @Test
    void sanitizeProfile_doesNothing_whenProfileDetailsIsNull() {
        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, null);
        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.invokeMethod(service, "sanitizeProfile", profile);
    }


    @Test
    void fetchFromDatabase_returnsNull_whenNoRecords() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(null);
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "fetchFromDatabase", "user-1");
        assertNull(result);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(Collections.emptyList());
        result = ReflectionTestUtils.invokeMethod(service, "fetchFromDatabase", "user-1");
        assertNull(result);
    }

    @Test
    void fetchFromDatabase_returnsRecordWithParsedProfileDetails_whenValidJson() throws Exception {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ProjectUtil projectUtil = mock(ProjectUtil.class);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "projectUtil", projectUtil);
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.PROFILE_DETAILS, "{\"email\":\"test@example.com\"}");
        List<Map<String, Object>> records = List.of(record);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(records);
        Map<String, Object> parsed = Map.of("email", "test@example.com");
        when(projectUtil.parseMap("{\"email\":\"test@example.com\"}")).thenReturn(parsed);
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "fetchFromDatabase", "user-2");
        assertNotNull(result);
        assertEquals(parsed, result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void fetchFromDatabase_removesProfileDetails_whenJsonInvalid() throws Exception {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ProjectUtil projectUtil = mock(ProjectUtil.class);
        Logger logger = mock(Logger.class);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "projectUtil", projectUtil);
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.PROFILE_DETAILS, "{invalid_json}");
        List<Map<String, Object>> records = List.of(record);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(records);
        when(projectUtil.parseMap("{invalid_json}")).thenThrow(new IOException("fail"));
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "fetchFromDatabase", "user-3");
        assertNotNull(result);
        assertFalse(result.containsKey(Constants.PROFILE_DETAILS));
    }

    @Test
    void fetchFromDatabase_leavesProfileDetailsNull_whenProfileDetailsIsNull() throws Exception {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ProjectUtil projectUtil = mock(ProjectUtil.class);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        ReflectionTestUtils.setField(service, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(service, "projectUtil", projectUtil);
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.PROFILE_DETAILS, null);
        List<Map<String, Object>> records = List.of(record);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(records);
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "fetchFromDatabase", "user-4");
        assertNotNull(result);
        assertNull(result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void validateFields_returnsEmptyString_whenAllMandatoryFieldsPresent() {
        Map<String, Object> data = Map.of("degree", "MSc", "institute", "Test University");
        String mandatoryFields = "degree,institute";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, false);
        assertEquals("", result);
    }

    @Test
    void validateFields_returnsErrorMessage_whenMandatoryFieldMissing() {
        Map<String, Object> data = Map.of("degree", "MSc");
        String mandatoryFields = "degree,institute";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, false);
        assertTrue(result.contains("institute is mandatory"));
    }

    @Test
    void validateFields_skipsEndDate_whenCurrentlyWorkingIsTrueAndAllowSkipEndDate() {
        Map<String, Object> data = new HashMap<>();
        data.put("degree", "MSc");
        data.put("endDate", "");
        data.put("currentlyWorking", "true");
        String mandatoryFields = "degree,endDate";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, true);
        assertEquals("", result);
    }

    @Test
    void validateFields_requiresEndDate_whenCurrentlyWorkingIsFalse() {
        Map<String, Object> data = new HashMap<>();
        data.put("degree", "MSc");
        data.put("endDate", "");
        data.put("currentlyWorking", "false");
        String mandatoryFields = "degree,endDate";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, true);
        assertTrue(result.contains("endDate is mandatory"));
    }

    @Test
    void validateFields_handlesBlankMandatoryFields() {
        Map<String, Object> data = Map.of("degree", "MSc");
        String mandatoryFields = "";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, false);
        assertEquals(" is mandatory. ", result);
    }

    @Test
    void validateFields_handlesNullValues() {
        Map<String, Object> data = new HashMap<>();
        data.put("degree", null);
        String mandatoryFields = "degree";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, false);
        assertTrue(result.contains("degree is mandatory"));
    }

    @Test
    void validateFields_handlesMultipleMissingFields() {
        Map<String, Object> data = new HashMap<>();
        String mandatoryFields = "degree,institute";
        ProfileServiceImpl service = new ProfileServiceImpl();
        String result = ReflectionTestUtils.invokeMethod(service, "validateFields", data, mandatoryFields, false);
        assertTrue(result.contains("degree is mandatory"));
        assertTrue(result.contains("institute is mandatory"));
    }

    @Test
    void getIssuedCertificateCount_returnsCachedValue_whenCacheHit() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        String userId = "user-1";
        when(cacheService.getCache("user:certCount:" + userId)).thenReturn("7");
        int count = ReflectionTestUtils.invokeMethod(service, "getIssuedCertificateCount", userId);
        assertEquals(7, count);
    }

    @Test
    void getIssuedCertificateCount_returnsSumOfCertificates_whenNoCache() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);

        String userId = "user-2";
        when(cacheService.getCache("user:certCount:" + userId)).thenReturn(null);

        Map<String, Object> courseRecord = new HashMap<>();
        courseRecord.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("cert1", "cert2"));
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENROLMENTS), anyMap(), anyList(), eq(userId)))
                .thenReturn(List.of(courseRecord));

        Map<String, Object> eventRecord = new HashMap<>();
        eventRecord.put(Constants.STATUS, 2);
        eventRecord.put(Constants.PROGRESS_KEY, 100);
        eventRecord.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("cert3"));
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENTITY_ENROLMENTS), anyMap(), anyList(), eq(userId)))
                .thenReturn(List.of(eventRecord));

        int count = ReflectionTestUtils.invokeMethod(service, "getIssuedCertificateCount", userId);
        assertEquals(3, count);
        verify(cacheService).putCache("user:certCount:" + userId, "3");
    }

    @Test
    void getIssuedCertificateCount_returnsZero_whenNoCertificatesAndNoCache() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);

        String userId = "user-3";
        when(cacheService.getCache("user:certCount:" + userId)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENROLMENTS), anyMap(), anyList(), eq(userId)))
                .thenReturn(Collections.emptyList());
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENTITY_ENROLMENTS), anyMap(), anyList(), eq(userId)))
                .thenReturn(Collections.emptyList());

        int count = ReflectionTestUtils.invokeMethod(service, "getIssuedCertificateCount", userId);
        assertEquals(0, count);
        verify(cacheService).putCache("user:certCount:" + userId, "0");
    }

    @Test
    void getIssuedCertificateCount_returnsZero_whenCacheValueIsNotInteger() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        String userId = "user-4";
        when(cacheService.getCache("user:certCount:" + userId)).thenReturn("not-a-number");
        int count = ReflectionTestUtils.invokeMethod(service, "getIssuedCertificateCount", userId);
        assertEquals(0, count);
    }

    @Test
    void getIssuedCertificateCount_returnsZero_whenExceptionThrown() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        String userId = "user-5";
        when(cacheService.getCache("user:certCount:" + userId)).thenThrow(new RuntimeException("Redis error"));
        int count = ReflectionTestUtils.invokeMethod(service, "getIssuedCertificateCount", userId);
        assertEquals(0, count);
    }

    @Test
    void getIssuedCertificateCount_ignoresEventRecordsWithNonMatchingStatusOrProgress() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        String userId = "user-6";
        when(cacheService.getCache("user:certCount:" + userId)).thenReturn(null);
        Map<String, Object> eventRecord1 = new HashMap<>();
        eventRecord1.put(Constants.STATUS, 1); // Not 2
        eventRecord1.put(Constants.PROGRESS_KEY, 100);
        eventRecord1.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("certA"));
        Map<String, Object> eventRecord2 = new HashMap<>();
        eventRecord2.put(Constants.STATUS, 2);
        eventRecord2.put(Constants.PROGRESS_KEY, 50); // Not 100
        eventRecord2.put(Constants.ISSUED_CERTIFICATES_KEY, List.of("certB"));
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENROLMENTS), anyMap(), anyList(), eq(userId)))
                .thenReturn(Collections.emptyList());
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENTITY_ENROLMENTS), anyMap(), anyList(), eq(userId)))
                .thenReturn(List.of(eventRecord1, eventRecord2));
        int count = ReflectionTestUtils.invokeMethod(service, "getIssuedCertificateCount", userId);
        assertEquals(0, count);
        verify(cacheService).putCache("user:certCount:" + userId, "0");
    }

    @Test
    void getUserKarmaPoints_returnsCachedValue_whenCacheHit() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        String userId = "user-1";
        when(cacheService.getCache("user:karmaPoints:" + userId)).thenReturn("42");
        int points = ReflectionTestUtils.invokeMethod(service, "getUserKarmaPoints", userId);
        assertEquals(42, points);
        verify(cacheService).getCache("user:karmaPoints:" + userId);
        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void getUserKarmaPoints_returnsValueFromDatabase_whenCacheMiss() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        String userId = "user-2";
        when(cacheService.getCache("user:karmaPoints:" + userId)).thenReturn(null);
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.TOTAL_POINTS, 17);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), eq(userId)))
                .thenReturn(List.of(record));
        int points = ReflectionTestUtils.invokeMethod(service, "getUserKarmaPoints", userId);
        assertEquals(17, points);
        verify(cacheService).putCache("user:karmaPoints:" + userId, "17");
    }

    @Test
    void getUserKarmaPoints_returnsZero_whenNoRecordsInDatabase() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        CassandraOperation cassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "cassandraOperation", cassandraOperation);
        String userId = "user-3";
        when(cacheService.getCache("user:karmaPoints:" + userId)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), anyList(), eq(userId)))
                .thenReturn(Collections.emptyList());
        int points = ReflectionTestUtils.invokeMethod(service, "getUserKarmaPoints", userId);
        assertEquals(0, points);
        verify(cacheService).putCache("user:karmaPoints:" + userId, "0");
    }

    @Test
    void getUserKarmaPoints_returnsZero_whenCacheValueIsNotInteger() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        String userId = "user-4";
        when(cacheService.getCache("user:karmaPoints:" + userId)).thenReturn("not-a-number");
        int points = ReflectionTestUtils.invokeMethod(service, "getUserKarmaPoints", userId);
        assertEquals(0, points);
    }

    @Test
    void getUserKarmaPoints_returnsZero_whenExceptionThrown() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        String userId = "user-5";
        when(cacheService.getCache("user:karmaPoints:" + userId)).thenThrow(new RuntimeException("Redis error"));
        int points = ReflectionTestUtils.invokeMethod(service, "getUserKarmaPoints", userId);
        assertEquals(0, points);
    }

    @Test
    void getSortingComparator_returnsServiceHistoryComparator_andSortsByStartDateDescending() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(service, "getSortingComparator", Constants.SERVICE_HISTORY);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of(Constants.START_DATE, "2022-01-01T00:00:00Z"));
        data.add(Map.of(Constants.START_DATE, "2023-01-01T00:00:00Z"));
        data.sort(comparator.reversed());
        assertEquals("2023-01-01T00:00:00Z", data.get(0).get(Constants.START_DATE));
    }

    @Test
    void getSortingComparator_returnsEducationalQualificationsComparator_andSortsByStartYearDescending() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(service, "getSortingComparator", Constants.EDUCATIONAL_QUALIFICATIONS);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of(Constants.START_YEAR, "2018"));
        data.add(Map.of(Constants.START_YEAR, "2020"));
        data.sort(comparator.reversed());
        assertEquals("2020", data.get(0).get(Constants.START_YEAR));
    }

    @Test
    void getSortingComparator_returnsAchievementsComparator_andSortsByIssuedDateDescending() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(service, "getSortingComparator", Constants.ACHIVEMENTS);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of(Constants.ISSUED_DATE, "2021-05-01T00:00:00Z"));
        data.add(Map.of(Constants.ISSUED_DATE, "2022-05-01T00:00:00Z"));
        data.sort(comparator.reversed());
        assertEquals("2022-05-01T00:00:00Z", data.get(0).get(Constants.ISSUED_DATE));
    }

    @Test
    void getSortingComparator_returnsNullForUnknownContextType() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(service, "getSortingComparator", "unknownType");
        assertNull(comparator);
    }

    @Test
    void getSortingComparator_handlesMissingFieldsGracefully() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(service, "getSortingComparator", Constants.EDUCATIONAL_QUALIFICATIONS);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(new HashMap<>()); // missing START_YEAR
        data.add(Map.of(Constants.START_YEAR, "2020"));
        assertThrows(NumberFormatException.class, () -> data.sort(comparator));
    }

    @Test
    void sortContextData_sortsListDescending_whenComparatorExists() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(Map.of(Constants.START_DATE, "2022-01-01T00:00:00Z"));
        dataList.add(Map.of(Constants.START_DATE, "2023-01-01T00:00:00Z"));
        ReflectionTestUtils.invokeMethod(service, "sortContextData", dataList, Constants.SERVICE_HISTORY);
        assertEquals("2023-01-01T00:00:00Z", dataList.get(0).get(Constants.START_DATE));
    }

    @Test
    void sortContextData_doesNotSort_whenComparatorIsNull() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(Map.of("field", "A"));
        dataList.add(Map.of("field", "B"));
        List<Map<String, Object>> original = new ArrayList<>(dataList);
        ReflectionTestUtils.invokeMethod(service, "sortContextData", dataList, "unknownType");
        assertEquals(original, dataList);
    }

    @Test
    void sortContextData_handlesEmptyList() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        List<Map<String, Object>> dataList = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(service, "sortContextData", dataList, Constants.SERVICE_HISTORY);
        assertTrue(dataList.isEmpty());
    }

    @Test
    void sortContextData_throwsException_whenFieldMissing() {
        ProfileServiceImpl service = new ProfileServiceImpl();
        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(new HashMap<>());
        dataList.add(Map.of(Constants.START_YEAR, "2020"));
        assertThrows(NumberFormatException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "sortContextData", dataList, Constants.EDUCATIONAL_QUALIFICATIONS)
        );
    }
}
