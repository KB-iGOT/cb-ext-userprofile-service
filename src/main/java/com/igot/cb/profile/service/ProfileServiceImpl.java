package com.igot.cb.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiRespParam;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class ProfileServiceImpl implements ProfileService {

    @Autowired
    private AccessTokenValidator accessTokenValidator;

    @Autowired
    private CbServerProperties serverConfig;

    @Autowired
    private CassandraOperation cassandraOperation;

    @Autowired
    private CacheService cacheService;

    private Logger logger = LoggerFactory.getLogger(ProfileServiceImpl.class);

    public String validateUserExtendedProfileRequest(Map<String, Object> requestData) {
        if (requestData == null) {
            return "Request data is missing.";
        }

        List<String> errList = new ArrayList<>();

        if (validateFieldsForList(requestData, Constants.EDUCATIONAL_QUALIFICATIONS, serverConfig.getEducationalQualificationMandatoryFields(), errList)) {
            return buildErrorMessage(errList);
        }

        if (validateFieldsForList(requestData, Constants.ACHIVEMENTS, serverConfig.getAchievementsMandatoryFields(), errList)) {
            return buildErrorMessage(errList);
        }

        if (validateFieldsForList(requestData, Constants.SERVICE_HISTORY, serverConfig.getServiceHistoryMandatoryFields(), errList)) {
            return buildErrorMessage(errList);
        }

        return errList.isEmpty() ? "" : "Failed Due To Missing or Invalid Params - " + String.join(", ", errList) + ".";
    }

    private boolean validateFieldsForList(Map<String, Object> requestData, String listKey, String mandatoryFields, List<String> errList) {
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) requestData.get(listKey);

        if (dataList != null) {
            for (Map<String, Object> data : dataList) {
                String error = validateFields(data, mandatoryFields);
                if (!error.isEmpty()) {
                    errList.add(error);
                    return true;
                }
            }
        }
        return false;
    }

    private String validateFields(Map<String, Object> data, String mandatoryFields) {
        StringBuilder errorMessages = new StringBuilder();

        String[] fields = mandatoryFields.split(",");
        for (String field : fields) {
            String value = (String) data.get(field);
            if (StringUtils.isBlank(value)) {
                errorMessages.append(field).append(" is mandatory. ");
            }
        }

        return errorMessages.toString();
    }


    private String buildErrorMessage(List<String> errList) {
        return errList.isEmpty() ? "" : "Failed Due To Missing or Invalid Params - " + String.join(", ", errList) + ".";
    }

    @Override
    public ApiResponse saveExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = createDefaultResponse("api.extendedProfile.create");

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Invalid UserId in the request");
            return response;
        }

        String[] contextTypes = serverConfig.getContextType();
        String validationError = validateRequestContextTypes(requestData, contextTypes);
        if (StringUtils.isNotBlank(validationError)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg(validationError);
            return response;
        }


        String errMsg = validateUserExtendedProfileRequest(requestData);
        if (StringUtils.isNotBlank(errMsg)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setErrMsg(errMsg);
            return response;
        }


        List<Map<String, Object>> savedDataWithUUIDs = new ArrayList<>();

        for (String contextType : contextTypes) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);
            if (incomingList == null || incomingList.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> dataWithUUIDs = addUUIDsToData(incomingList);

            Map<String, Object> query = new HashMap<>();
            query.put(Constants.USERID_KEY, userId);
            query.put(Constants.CONTEXT_TYPE, contextType);

            List<Map<String, Object>> existingRows = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);

            List<Map<String, Object>> mergedList = mergeExistingAndNewData(existingRows, dataWithUUIDs, contextType);

            String finalJson = convertListToJson(mergedList, response);
            if (finalJson == null) {
                return response;
            }

            query.put(Constants.CONTEXT_DATA, finalJson);

            ApiResponse insertResponse =  (ApiResponse) cassandraOperation.insertRecord(Constants.DATABASE,
                    Constants.TABLE_USER_EXTENDED_PROFILE, query);

            if (!Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE))) {
                response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
                response.getParams().setErrMsg("Failed to insert or update context data for contextType: " + contextType);
                return response;
            }
            savedDataWithUUIDs.addAll(dataWithUUIDs);
        }

        if (!savedDataWithUUIDs.isEmpty()) {
            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESULT, savedDataWithUUIDs);
        } else {
            response.setResponseCode(HttpStatus.NO_CONTENT);
            response.getParams().setErrMsg("No data was saved.");
        }

        return response;
    }

    private List<Map<String, Object>> addUUIDsToData(List<Map<String, Object>> incomingList) {
        List<Map<String, Object>> dataWithUUIDs = new ArrayList<>();
        for (Map<String, Object> context : incomingList) {
            String uuid = UUID.randomUUID().toString();
            context.put(Constants.UUID, uuid);
            dataWithUUIDs.add(context);
        }
        return dataWithUUIDs;
    }

    private List<Map<String, Object>> mergeExistingAndNewData(List<Map<String, Object>> existingRows, List<Map<String, Object>> newData, String contextType) {
        List<Map<String, Object>> mergedList = new ArrayList<>(newData);

        if (existingRows != null && !existingRows.isEmpty()) {
            Map<String, Object> row = existingRows.get(0);
            String existingJson = (String) row.get(Constants.CONTEXT_DATA);
            try {
                List<Map<String, Object>> existingList = new ObjectMapper().readValue(
                        existingJson, new TypeReference<List<Map<String, Object>>>() {
                        });
                mergedList.addAll(existingList);
            } catch (IOException e) {
                mergedList = new ArrayList<>();
            }
        }

        Comparator<Map<String, Object>> comparator = getSortingComparator(contextType);
        if (comparator != null) {
            mergedList.sort(comparator.reversed());
        }

        return mergedList;
    }

    private String convertListToJson(List<Map<String, Object>> list, ApiResponse response) {
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (JsonProcessingException e) {
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("Failed to serialize merged context data: " + e.getMessage());
            return null;
        }
    }


    @Override
    public ApiResponse getExtendedProfileSummary(String userId, String userToken) {
        ApiResponse response = createDefaultResponse("api.extendedProfile.read");

        if (!isValidUser(userToken)) {
            return createErrorResponse("Invalid UserId in the request", HttpStatus.BAD_REQUEST);
        }

        String redisKey = buildSummaryRedisKey(userId);
        Map<String, Object> result = fetchSummaryFromCache(redisKey);

        if (result == null) {
            result = fetchSummaryFromDB(userId);
            if (result == null) {
                return createErrorResponse("No data found for user.", HttpStatus.NO_CONTENT);
            }
            cacheSummary(redisKey, result);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, result);
        return response;
    }

    private boolean isValidUser(String userToken) {
        return accessTokenValidator.fetchUserIdFromAccessToken(userToken) != null;
    }

    private ApiResponse createErrorResponse(String errMsg, HttpStatus status) {
        ApiResponse response = createDefaultResponse("api.extendedProfile.read");
        response.setResponseCode(status);
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrMsg(errMsg);
        return response;
    }

    private String buildSummaryRedisKey(String userId) {
        return "user:extendedProfile:all:" + userId;
    }

    private Map<String, Object> fetchSummaryFromCache(String redisKey) {
        try {
            Map<String, Object> cachedResult = getFromCache(redisKey);
            if (cachedResult != null && !cachedResult.isEmpty()) {
                logger.info("Cache hit for key: {}", redisKey);
                return cachedResult;
            }
            logger.info("Cache miss for key: {}", redisKey);
        } catch (Exception e) {
            logger.warn("Failed to fetch cache for key {}: {}", redisKey, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> fetchSummaryFromDB(String userId) {
        String[] contextTypes = serverConfig.getContextType();
        Map<String, Object> result = new HashMap<>();

        for (String contextType : contextTypes) {
            Map<String, Object> query = Map.of(
                    Constants.USERID_KEY, userId,
                    Constants.CONTEXT_TYPE, contextType
            );

            List<Map<String, Object>> rows = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);

            if (rows != null && !rows.isEmpty()) {
                String json = (String) rows.get(0).get(Constants.CONTEXT_DATA);
                try {
                    List<Map<String, Object>> contextData = new ObjectMapper().readValue(
                            json, new TypeReference<>() {});

                    List<Map<String, Object>> summary = contextData.stream().limit(2).collect(Collectors.toList());
                    Map<String, Object> contextSummary = new HashMap<>();
                    contextSummary.put(Constants.COUNT, contextData.size());
                    contextSummary.put(Constants.DATA, summary);
                    result.put(contextType, contextSummary);

                } catch (IOException e) {
                    logger.error("Error parsing context data for {}: {}", contextType, e.getMessage());
                    return null;
                }
            }
        }

        result.put(Constants.USERID_KEY, userId);
        return result.isEmpty() ? null : result;
    }

    private void cacheSummary(String redisKey, Map<String, Object> result) {
        try {
            cacheService.putCache(redisKey, result);
            logger.info("Cached extended profile summary for key: {}", redisKey);
        } catch (Exception e) {
            logger.warn("Failed to cache extended profile summary for key {}: {}", redisKey, e.getMessage());
        }
    }

    private Comparator<Map<String, Object>> getSortingComparator(String contextType) {
        switch (contextType) {
            case Constants.SERVICE_HISTORY:
                return Comparator.comparing(map -> LocalDate.parse((String) map.get(Constants.START_DATE)));
            case Constants.EDUCATIONAL_QUALIFICATIONS:
                return Comparator.comparing(map -> Integer.parseInt((String) map.get(Constants.START_YEAR)));
            case Constants.ACHIVEMENTS:
                return Comparator.comparing(map -> LocalDate.parse((String) map.get(Constants.ISSUED_DATE)));
            default:
                return null;
        }
    }

    @Override
    public ApiResponse readFullExtendedProfile(String userId, String contextType, String userToken) {
        ApiResponse response = createDefaultResponse("api.extendedProfile.read");

        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (userIdFromToken == null) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Invalid UserId in the request");
            return response;
        }

        String redisKey = "user:extendedProfile:" + contextType + ":" + userId;
        List<Map<String, Object>> contextData = null;

        try {
            Map<String, Object> cachedMap = getFromCache(redisKey);
            if (cachedMap != null && cachedMap.containsKey(contextType)) {
                logger.info("Cache hit for key: {}", redisKey);
                contextData = extractContextData(cachedMap.get(contextType));
            } else {
                logger.info("Cache miss for key: {}", redisKey);
            }
        } catch (Exception e) {
            logger.warn("Error reading from cache for key {}: {}", redisKey, e.getMessage());
        }

        if (contextData == null) {
            contextData = fetchExtendedProfileFromDB(userId, contextType);
            if (contextData != null) {
                cacheService.putCache(redisKey, Map.of(contextType, contextData));
            } else {
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setErrMsg("No data found for user.");
                return response;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put(contextType, contextData);
        result.put(Constants.USER_ID_RQST, userId);
        result.put(Constants.COUNT, contextData instanceof Collection ? ((Collection<?>) contextData).size() : 1);
        response.put(Constants.RESPONSE, result);
        response.setResponseCode(HttpStatus.OK);

        return response;
    }


    @Override
    public ApiResponse updateExtendedProfile(Map<String, Object> request, String userToken) {

        ApiResponse response = createDefaultResponse("api.extendedProfile.update");

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String[] contextTypes = serverConfig.getContextType();
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (userIdFromToken == null) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Invalid UserId in the request");
            return response;
        }

        for (String contextType : contextTypes) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);

            if (incomingList == null || incomingList.isEmpty()) {
                continue;
            }
            response = updateContextData(userId, contextType, incomingList);
            if (response.getResponseCode() != HttpStatus.OK) {
                return response;
            }
        }
        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    /**
     * This method updates the context data for a specific user and context type.
     */
    private ApiResponse updateContextData(String userId, String contextType,
                                          List<Map<String, Object>> incomingList) {

        ApiResponse response = fetchExistingData(userId, contextType);
        if (response.getResponseCode() != HttpStatus.OK) {
            return response;
        }

        List<Map<String, Object>> existingDataList = (List<Map<String, Object>>) response.get(Constants.EXISTING_DATA);


        response = mergeData(existingDataList, incomingList);
        if (response.getResponseCode() != HttpStatus.OK) {
            return response;
        }

        Comparator<Map<String, Object>> comparator = getSortingComparator(contextType);
        if (comparator != null) {
            existingDataList.sort(comparator.reversed());
        }

        String updatedJson = serializeDataToJson(existingDataList);
        if (updatedJson == null) {
            return errorResponse("Failed to serialize updated data.");
        }

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put(Constants.CONTEXT_DATA, updatedJson);
        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put(Constants.USERID_KEY, userId);
        compositeKey.put(Constants.CONTEXT_TYPE, contextType);


        Map<String, Object> updateResult = cassandraOperation.updateRecordByCompositeKey(
                Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, updateRequest, compositeKey);

        if (!Constants.SUCCESS.equals(updateResult.get(Constants.RESPONSE))) {
            return errorResponse("Failed to update data for contextType: " + contextType);
        }

        return response;
    }

    /**
     * This method fetches the existing data for the given user and context type.
     */
    private ApiResponse fetchExistingData(String userId, String contextType) {
        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put(Constants.USERID_KEY, userId);
        compositeKey.put(Constants.CONTEXT_TYPE, contextType);

        List<Map<String, Object>> existingRecords = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, compositeKey, null, null);

        if (existingRecords.isEmpty()) {
            return errorResponse("No existing records found for the given contextType: " + contextType);
        }

        String existingDataJson = (String) existingRecords.get(0).get(Constants.CONTEXT_DATA);
        if (existingDataJson == null || existingDataJson.isEmpty()) {
            return errorResponse("No context data found for the given contextType.");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> existingDataList = mapper.readValue(existingDataJson, new TypeReference<List<Map<String, Object>>>() {
            });
            ApiResponse response = new ApiResponse();
            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.EXISTING_DATA, existingDataList);
            return response;
        } catch (IOException e) {
            return errorResponse("Error parsing existing context data.");
        }
    }

    /**
     * This method merges incoming data with existing data based on the UUID.
     */
    private ApiResponse mergeData(List<Map<String, Object>> existingDataList,
                                  List<Map<String, Object>> incomingList) {
        ApiResponse mergeResponse = new ApiResponse();

        Map<String, Map<String, Object>> existingMap = existingDataList.stream()
                .filter(entry -> entry.get(Constants.UUID) != null)
                .collect(Collectors.toMap(
                        entry -> (String) entry.get(Constants.UUID),
                        entry -> entry));

        for (Map<String, Object> incomingItem : incomingList) {
            String uuid = (String) incomingItem.get(Constants.UUID);
            if (uuid != null && existingMap.containsKey(uuid)) {
                Map<String, Object> existingItem = existingMap.get(uuid);

                for (Map.Entry<String, Object> entry : incomingItem.entrySet()) {
                    if (!"uuid".equals(entry.getKey())) {
                        existingItem.put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                return errorResponse("Invalid or missing UUID in incoming data.");
            }
        }

        mergeResponse.setResponseCode(HttpStatus.OK);
        return mergeResponse;
    }

    /**
     * This method serializes a list of data to JSON.
     */
    private String serializeDataToJson(List<Map<String, Object>> dataList) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(dataList);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Utility method to generate error response with a custom message.
     */
    private ApiResponse errorResponse(String errorMessage) {
        ApiResponse response = new ApiResponse();
        response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.getParams().setErrMsg(errorMessage);
        return response;
    }

    @Override
    public ApiResponse deleteExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = createDefaultResponse("api.extendedProfile.delete");

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String[] contextTypes = serverConfig.getContextType();

        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (userIdFromToken == null) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Invalid UserId in the request");
            return response;
        }

        for (String contextType : contextTypes) {
            List<Map<String, Object>> uuidsToDeleteMap = (List<Map<String, Object>>) requestData.get(contextType);

            if (uuidsToDeleteMap == null || uuidsToDeleteMap.isEmpty()) {
                continue;
            }

            Set<String> uuidsToDelete = uuidsToDeleteMap.stream()
                    .map(map -> (String) map.get(Constants.UUID))
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            Map<String, Object> compositeKey = new HashMap<>();
            compositeKey.put(Constants.USERID_KEY, userId);
            compositeKey.put(Constants.CONTEXT_TYPE, contextType);

            List<Map<String, Object>> existingRecords = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, compositeKey, null, null);

            if (existingRecords.isEmpty()) {
                continue;
            }

            String contextJson = (String) existingRecords.get(0).get(Constants.CONTEXT_DATA);
            if (StringUtils.isBlank(contextJson)) {
                continue;
            }

            List<Map<String, Object>> contextList;
            try {
                contextList = new ObjectMapper().readValue(contextJson, new TypeReference<List<Map<String, Object>>>() {
                });
            } catch (IOException e) {
                return errorResponse(response, "Failed to parse existing context data.");
            }

            contextList.removeIf(item ->
                    item.get(Constants.UUID) != null &&
                            uuidsToDelete.contains(String.valueOf(item.get(Constants.UUID))));

            Comparator<Map<String, Object>> comparator = getSortingComparator(contextType);
            if (comparator != null) {
                contextList.sort(comparator.reversed());
            }

            String updatedJson;
            try {
                updatedJson = new ObjectMapper().writeValueAsString(contextList);
            } catch (JsonProcessingException e) {
                return errorResponse(response, "Failed to serialize updated context data.");
            }

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put(Constants.CONTEXT_DATA, updatedJson);
            Map<String, Object> updateResult = cassandraOperation.updateRecordByCompositeKey(
                    Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, updateRequest, compositeKey);

            if (!Constants.SUCCESS.equals(updateResult.get(Constants.RESPONSE))) {
                return errorResponse(response, "Failed to update context data for contextType: " + contextType);
            }
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    @Override
    public ApiResponse getBasicProfile(String userId, String userToken) {
        ApiResponse response = createDefaultResponse("api.getBasicProfile.read");

        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (userIdFromToken == null) {
            return errorResponse("Invalid or missing access token");
        }

        boolean isSelfUser = userIdFromToken.equalsIgnoreCase(userId);
        String cacheKey = Constants.USER + ":basicProfile:" + userId;

        try {
            Map<String, Object> userProfile = getFromCache(cacheKey);
            if (userProfile == null) {
                userProfile = fetchFromDatabase(userId);
                if (userProfile == null) {
                    return responseWithEmptyProfile(response);
                }
                cacheService.putCache(cacheKey, userProfile);
            }

            double completion = calculateProfileCompletionPercentage(
                    userProfile,
                    Constants.PROFILE_DETAILS_LOWERCASE,
                    userId,
                    userToken);
            userProfile.put("profileCompletion", completion);
            if (!isSelfUser) {
                sanitizeProfile(userProfile);
            }

            response.setResponse(userProfile);
        } catch (Exception e) {
            logger.error("Error fetching basic profile for userId: {}", userId, e);
            return errorResponse("Internal server error while fetching profile");
        }

        return response;
    }

    private Map<String, Object> getFromCache(String cacheKey) {
        String cachedJson = cacheService.getCache(cacheKey);
        if (cachedJson == null) return null;

        try {
            return new ObjectMapper().readValue(cachedJson, Map.class);
        } catch (IOException e) {
            logger.warn("Failed to parse cached profile for key: {}", cacheKey, e);
            return null;
        }
    }

    private Map<String, Object> fetchFromDatabase(String userId) {
        Map<String, Object> queryParams = Map.of(Constants.ID, userId);

        List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.USER,
                queryParams,
                serverConfig.getBasicProfileFields(),
                null
        );

        if (records == null || records.isEmpty()) return null;

        Map<String, Object> record = records.get(0);
        String profileDetailsJson = (String) record.get(Constants.PROFILE_DETAILS_LOWERCASE);

        if (profileDetailsJson != null) {
            try {
                Map<String, Object> profileDetailsMap = new ObjectMapper().readValue(profileDetailsJson, Map.class);
                record.put(Constants.PROFILE_DETAILS_LOWERCASE, profileDetailsMap);
            } catch (IOException e) {
                logger.warn("Invalid profileDetails JSON for userId: {}", userId, e);
                record.remove(Constants.PROFILE_DETAILS);
            }
        }

        return record;
    }

    private void sanitizeProfile(Map<String, Object> profile) {
        Object profileDetailsObj = profile.get(Constants.PROFILE_DETAILS_LOWERCASE);
        if (profileDetailsObj instanceof Map<?, ?> detailsMap && ((Map<?, ?>) profileDetailsObj).containsKey(Constants.PERSONAL_DETAILS)) {
            detailsMap.remove("personalDetails");
            logger.info("Removed personalDetails for non-self user.");
        }
    }

    private ApiResponse responseWithEmptyProfile(ApiResponse response) {
        response.setResponseCode(HttpStatus.NOT_FOUND);
        response.put("response", Collections.emptyMap());
        return response;
    }

    private ApiResponse errorResponse(ApiResponse response, String errorMessage) {
        response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.getParams().setErrMsg(errorMessage);
        return response;
    }

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setParams(new ApiRespParam(UUID.randomUUID().toString()));
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(LocalDate.now().toString());
        return response;
    }

    private String validateRequestContextTypes(Map<String, Object> requestData, String[] contextTypes) {
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(contextTypes));
        allowedKeys.add(Constants.USER_ID_RQST);

        Optional<String> invalidKey = requestData.keySet().stream()
                .filter(key -> !allowedKeys.contains(key))
                .findFirst();

        return invalidKey.map(key -> "Invalid context type in request: " + key).orElse(null);
    }

    private List<Map<String, Object>> fetchExtendedProfileFromDB(String userId, String contextType) {
        logger.info("Fetching extended profile from DB for userId: {}, contextType: {}", userId, contextType);
        Map<String, Object> query = new HashMap<>();
        query.put(Constants.USERID_KEY, userId);
        query.put(Constants.CONTEXT_TYPE, contextType);

        List<Map<String, Object>> rows = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.DATABASE, Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);

        if (rows == null || rows.isEmpty()) return null;

        String contextDataJson = (String) rows.get(0).get(Constants.CONTEXT_DATA);

        try {
            return new ObjectMapper().readValue(
                    contextDataJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            logger.error("Failed to parse context data JSON for userId: {}, contextType: {}: {}", userId, contextType, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractContextData(Object contextDataRaw) {
        if (contextDataRaw instanceof List<?>) {
            return (List<Map<String, Object>>) contextDataRaw;
        }
        return List.of();
    }

    public double calculateProfileCompletionPercentage(Map<String, Object> profileData, String nestedFieldKey, String userId, String userToken) {
        List<String> requiredFields = serverConfig.getProfileCompletionRequiredFields();
        if (profileData == null || requiredFields == null || requiredFields.isEmpty()) {
            return 0.0;
        }

        double totalCompletion = 0.0;

        Map<String, Object> nestedData = getNestedData(profileData, nestedFieldKey);

        for (String field : requiredFields) {
            boolean isFilled;
            try {
                if (isExtendedProfileField(field)) {
                    isFilled = hasExtendedProfileData(userId, field, userToken);
                } else {
                    isFilled = isFieldPresent(profileData, nestedData, field);
                }
            } catch (Exception e) {
                logger.warn("Exception checking field '{}' for user '{}': {}", field, userId, e.getMessage());
                isFilled = false;
            }

            if (isFilled) {
                totalCompletion += serverConfig.getFieldWeight();
            }
        }

        totalCompletion = Math.min(totalCompletion, 100.0);

        return Math.round(totalCompletion * 10.0) / 10.0;
    }

    private Map<String, Object> getNestedData(Map<String, Object> profileData, String nestedFieldKey) {
        Object nested = profileData.get(nestedFieldKey);
        if (nested instanceof Map) {
            return (Map<String, Object>) nested;
        }
        return Collections.emptyMap();
    }

    private boolean isExtendedProfileField(String field) {
        return serverConfig.getExtendedFieldsConfig().stream()
                .anyMatch(f -> f.equalsIgnoreCase(field));
    }


    private boolean isFieldPresent(Map<String, Object> profileData, Map<String, Object> nestedData, String field) {
        Object value = profileData.getOrDefault(field, nestedData.get(field));
        return value != null && !value.toString().trim().isEmpty();
    }

    private boolean hasExtendedProfileData(String userId, String contextType, String userToken) {
        try {
            ApiResponse response = readFullExtendedProfile(userId, contextType, userToken);

            if (response != null && response.getResponseCode() == HttpStatus.OK) {
                Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
                Object contextData = result.get(contextType);

                return contextData instanceof Collection && !((Collection<?>) contextData).isEmpty();
            } else {
                logger.warn("No extended profile data found for user {} and context {}", userId, contextType);
            }
        } catch (Exception e) {
            logger.error("Exception while reading extended profile for user {} and context {}: {}", userId, contextType, e.getMessage(), e);
        }
        return false;
    }






}
