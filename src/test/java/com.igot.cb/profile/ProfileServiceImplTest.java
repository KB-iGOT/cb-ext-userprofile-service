package com.igot.cb.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiRespParam;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProfileServiceImplTest {


    @InjectMocks
    private ProfileServiceImpl extendedProfileService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CbServerProperties serverConfig;

    @Mock
    private CacheService cacheService;

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String USER_ID = "0ba1704d-eafb-4590-98ee-e973a535e71e";
    private static final String TOKEN = "";

    @BeforeEach
    void setup() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    public void testSaveWithValidEducationalQualifications() throws Exception {

        Map<String, Object> request = loadJson("testData/valid-education-request.json");
        when(serverConfig.getContextType())
                .thenReturn(new String[]{"educationalQualifications", "achievements", "serviceHistory"});
        when(serverConfig.getEducationalQualificationMandatoryFields())
                .thenReturn("degree,institutionName,startYear,endYear");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(cassandraOperation.insertRecord(any(), any(), anyMap()))
                .thenReturn(buildSuccessResponse());

        ApiResponse response = extendedProfileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
    }

    @Test
    public void testSaveWithInvalidToken() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(null);

        Map<String, Object> request = loadJson("testData/valid-education-request.json");

        ApiResponse response = extendedProfileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    public void testValidationFailureWithMissingFields() throws Exception {
        Map<String, Object> request = loadJson("testData/invalid-education-request.json");
        when(serverConfig.getContextType())
                .thenReturn(new String[]{"educationalQualifications", "achievements", "serviceHistory"});
        when(serverConfig.getEducationalQualificationMandatoryFields())
                .thenReturn("degree,institutionName,startYear,endYear");
        ApiResponse response = extendedProfileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(StringUtils.isNotBlank(response.getParams().getErrMsg()));
    }

    @Test
    public void testSaveWithNoContextData() throws Exception {
        Map<String, Object> request = loadJson("testData/no-context-data-request.json");
        when(serverConfig.getContextType())
                .thenReturn(new String[]{"educationalQualifications", "achievements", "serviceHistory"});
        ApiResponse response = extendedProfileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().startsWith("Invalid context type in request"));
    }

    @Test
    public void testInsertFailure() throws Exception {
        Map<String, Object> request = loadJson("testData/valid-education-request.json");

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse failureResponse = new ApiResponse();
        failureResponse.put(Constants.RESPONSE, "FAILED");
        when(serverConfig.getContextType())
                .thenReturn(new String[]{"educationalQualifications", "achievements", "serviceHistory"});
        when(serverConfig.getEducationalQualificationMandatoryFields())
                .thenReturn("degree,institutionName,startYear,endYear");
        when(cassandraOperation.insertRecord(any(), any(), anyMap()))
                .thenReturn(failureResponse);

        ApiResponse response = extendedProfileService.saveExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Failed to insert"));
    }

    @Test
    public void testUpdateExtendedProfileWithValidData() throws Exception {
        Map<String, Object> request = loadJson("testData/valid-update-request.json");

        when(serverConfig.getContextType()).thenReturn(new String[]{"educationalQualifications", "achievements", "serviceHistory"});

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(Collections.singletonList(buildExistingData()));

        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), anyMap(), anyMap()))
                .thenReturn(Collections.singletonMap(Constants.RESPONSE, Constants.SUCCESS));

        ApiResponse response = extendedProfileService.updateExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
    public void testUpdateExtendedProfileWithInvalidToken() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(TOKEN)).thenReturn(null);

        Map<String, Object> request = loadJson("testData/valid-update-request.json");

        ApiResponse response = extendedProfileService.updateExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    public void testUpdateExtendedProfileWithMissingContextData() throws Exception {
        Map<String, Object> request = loadJson("testData/empty-update-request.json");

        when(serverConfig.getContextType()).thenReturn(new String[]{"educationalQualifications"});

        ApiResponse response = extendedProfileService.updateExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    public void testUpdateExtendedProfileDatabaseFailure() throws Exception {
        Map<String, Object> request = loadJson("testData/valid-update-request.json");

        when(serverConfig.getContextType()).thenReturn(new String[]{"educationalQualifications"});

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(Collections.singletonList(buildExistingData()));

        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), anyMap(), anyMap()))
                .thenReturn(Collections.singletonMap(Constants.RESPONSE, "FAILURE"));

        ApiResponse response = extendedProfileService.updateExtendedProfile(request, TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Failed to update data for contextType: educationalQualifications"));
    }

    @Test
    void testGetExtendedProfileSummary() throws Exception {
        String[] contextTypes = {"educationalQualifications", "achievements", "serviceHistory"};
        when(serverConfig.getContextType()).thenReturn(contextTypes);
        for (String contextType : contextTypes) {
            String fileName = contextType + ".json";
            String contextDataJson = readJson(fileName);

            Map<String, Object> dbRow = new HashMap<>();
            dbRow.put(Constants.CONTEXT_DATA, contextDataJson);
            List<Map<String, Object>> dbResult = Collections.singletonList(dbRow);

            when(cassandraOperation.getRecordsByPropertiesByKey(
                    eq(Constants.DATABASE),
                    eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                    argThat(query -> query.get(Constants.CONTEXT_TYPE).equals(contextType) &&
                            query.get(Constants.USERID_KEY).equals(USER_ID)),
                    any(), any())
            ).thenReturn(dbResult);
        }


        ApiResponse response = extendedProfileService.getExtendedProfileSummary(USER_ID, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult().get(Constants.RESULT);
        assertEquals(USER_ID, result.get(Constants.USERID_KEY));
        assertTrue(result.containsKey("educationalQualifications"));
        assertTrue(result.containsKey("achievements"));
        assertTrue(result.containsKey("serviceHistory"));
    }


    @ParameterizedTest
    @ValueSource(strings = {"educationalQualifications", "achievements", "serviceHistory"})
    void testReadFullExtendedProfile(String contextType) throws Exception {
        String contextDataJson = readJson(contextType + ".json");
        Map<String, Object> dbRow = new HashMap<>();
        dbRow.put(Constants.CONTEXT_DATA, contextDataJson);
        List<Map<String, Object>> dbResult = Collections.singletonList(dbRow);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.DATABASE),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(query ->
                        contextType.equals(query.get(Constants.CONTEXT_TYPE)) &&
                                USER_ID.equals(query.get(Constants.USERID_KEY))),
                any(), any()
        )).thenReturn(dbResult);

        ApiResponse response = extendedProfileService.readFullExtendedProfile(USER_ID, contextType, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult().get(Constants.RESULT);

        assertEquals(USER_ID, result.get(Constants.USER_ID_RQST));
        assertTrue(result.containsKey(contextType));

        List<Map<String, Object>> returnedData = (List<Map<String, Object>>) result.get(contextType);
        List<Map<String, Object>> expectedData = new ObjectMapper().readValue(contextDataJson, new TypeReference<>() {});

        assertEquals(expectedData.size(), returnedData.size());
        assertEquals(expectedData.get(0).get("uuid"), returnedData.get(0).get("uuid"));
    }

    @Test
    void testGetBasicProfile_SelfUser_CacheHit() throws IOException {
        String cacheKey = Constants.USER + ":basicProfile:" + USER_ID;
        String cachedJson = readJson("basicProfileSelf.json");
        when(cacheService.getCache(cacheKey)).thenReturn(cachedJson);

        ApiResponse response = extendedProfileService.getBasicProfile(USER_ID, TOKEN);
        verify(cacheService, times(1)).getCache(cacheKey);
        verify(cacheService, never()).putCache(anyString(), any());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("success", response.getParams().getStatus());
        assertEquals("Mayank212 Vats", ((Map<?, ?>) response.getResult()).get("firstName"));
    }

    @Test
    void testGetBasicProfile_SelfUser_DbHit() throws Exception {
        String cacheKey = "user:basicProfile:" + USER_ID;
        when(cacheService.getCache(cacheKey)).thenReturn(null);

        ObjectMapper mapper = new ObjectMapper();
        String dbJson = readJson("basicProfileDbRecord.json");
        Map<String, Object> dbRecord = loadJsonFromString(dbJson);

        Object profileDetailsObj = dbRecord.get(Constants.PROFILE_DETAILS_LOWERCASE);
        String profileDetailsJson = mapper.writeValueAsString(profileDetailsObj);
        dbRecord.put(Constants.PROFILE_DETAILS_LOWERCASE, profileDetailsJson);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.USER),
                anyMap(),
                anyList(),
                isNull()
        )).thenReturn(List.of(dbRecord));

        when(serverConfig.getBasicProfileFields()).thenReturn(List.of("firstName", "channel", "id", "profiledetails", "rootOrgId"));

        ApiResponse response = extendedProfileService.getBasicProfile(USER_ID, TOKEN);

        assertNotNull(response);
        assertEquals("api.getBasicProfile.read", response.getId());
        assertEquals(HttpStatus.OK, response.getResponseCode());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals(USER_ID, result.get("id"));
        assertTrue(result.containsKey(Constants.PROFILE_DETAILS_LOWERCASE));
        verify(cacheService).putCache(eq(cacheKey), anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"serviceHistory"})
    void testDeleteExtendedProfile_forMultipleContextTypes(String contextType) throws Exception {

        Map<String, Object> input = loadJson("testData/delete-ExtendedProfile.json");
        String contextDataJson = readJson("deleteContextData.json");

        Map<String, Object> dbRow = new HashMap<>();
        dbRow.put(Constants.CONTENT_TYPE_KEY, contextType);
        dbRow.put(Constants.USERID_KEY, USER_ID);
        dbRow.put(Constants.CONTEXT_DATA, contextDataJson);

        when(serverConfig.getContextType()).thenReturn(new String[] { contextType });
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(dbRow));
        when(cassandraOperation.updateRecordByCompositeKey(anyString(), anyString(), anyMap(), anyMap()))
                .thenReturn(Map.of(Constants.RESPONSE, Constants.SUCCESS));

        ApiResponse response = extendedProfileService.deleteExtendedProfile(input, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }



    private Map<String, Object> loadJson(String filePath) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
        return mapper.readValue(is, new TypeReference<>() {});
    }

    private ApiResponse buildSuccessResponse() {
        ApiResponse response = new ApiResponse();
        response.setParams(new ApiRespParam());
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    private Map<String, Object> buildExistingData() {
        Map<String, Object> record = new HashMap<>();
        record.put("contextData", "[{\"uuid\":\"225b109b-ccf4-46e5-9837-c313abdcc0e6\",\"startYear\":\"2012\"}]");
        return record;
    }

    private String readJson(String fileName) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/test/resources/testData/" + fileName)));
    }

    private Map<String, Object> loadJsonFromString(String jsonContent) throws Exception {
        return mapper.readValue(jsonContent, new TypeReference<>() {});
    }

}
