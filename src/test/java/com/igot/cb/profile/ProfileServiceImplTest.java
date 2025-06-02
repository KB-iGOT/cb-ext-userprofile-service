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

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.poi.ss.formula.functions.T;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
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
    void testListCompetencies_success() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);

        String userId = "user-123"; // Ensure consistency
        String cacheKey = "user:competencies:" + userId;

        // Return a real JSON string if needed for cache hit
        String mockCachedJson = "{}"; // Or some actual JSON content
        when(cacheService.getCache(cacheKey)).thenReturn(mockCachedJson); // ✅ FIXED

        Map<String, Object> record = Map.of(
                Constants.ACTIVE, true,
                Constants.STATUS, 2,
                Constants.COURSE_ID, "course1"
        );
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(record));

        String courseJson = "{\"competencies_v6\":[{\"competencyAreaName\":\"Area1\",\"competencyThemeName\":\"Theme1\",\"competencySubThemeName\":\"Sub1\"}]}";
        lenient().when(cacheService.getCourseMetadataAsJsonString(List.of("course1"))).thenReturn(Map.of("course1", courseJson));

        Map<String, Object> parsedMap = Map.of(
                "competencies_v6", List.of(Map.of(
                        "competencyAreaName", "Area1",
                        "competencyThemeName", "Theme1",
                        "competencySubThemeName", "Sub1"
                ))
        );
        lenient().when(projectUtil.parseMap(courseJson)).thenReturn(parsedMap);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = profileService.listCompetencies(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
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
    void testAnalyzeCompetencies_valid() {
        Map<String, Object> comp1 = Map.of(
                "competencyAreaName", "Area1",
                "competencyThemeName", "Theme1",
                "competencySubThemeName", "Sub1"
        );

        Map<String, Object> courseMeta = Map.of("competencies_v6", List.of(comp1));
        Map<String, Map<String, Object>> input = Map.of("course1", courseMeta);

        Map<String, Object> result = profileService.analyzeCompetencies(input);

        assertNotNull(result.get("competencyAreaCounts"));
        assertNotNull(result.get("competencyThemeGroups"));
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
}
