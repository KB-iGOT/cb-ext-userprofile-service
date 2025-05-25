package com.igot.cb.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("unchecked")
public class ProfileServiceImpl implements ProfileService {

    @Autowired
    private AccessTokenValidator accessTokenValidator;
    
    @Autowired
    private CbServerProperties serverConfig;
    
    @Autowired
    private CassandraOperation cassandraOperation;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private ObjectMapper mapper;
    
    @Autowired
    private ProjectUtil projectUtil;

    private static final Logger logger = LoggerFactory.getLogger(ProfileServiceImpl.class);

    // -------------------- Service METHODS --------------------

    @Override
    public ApiResponse saveExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.create");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, "Invalid UserId in the request", HttpStatus.BAD_REQUEST);
            return response;
        }

        String validationError = validateRequestContextTypes(requestData, serverConfig.getContextType());
        if (StringUtils.isNotBlank(validationError)) {
            ProjectUtil.errorResponse(response, validationError, HttpStatus.BAD_REQUEST);
            return response;
        }

        String errMsg = validateUserExtendedProfileRequest(requestData);
        if (StringUtils.isNotBlank(errMsg)) {
            ProjectUtil.errorResponse(response, errMsg, HttpStatus.BAD_REQUEST);
            return response;
        }

        List<Map<String, Object>> savedDataWithUUIDs = new ArrayList<>();
        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);
            if (incomingList == null || incomingList.isEmpty())
                continue;

            List<Map<String, Object>> dataWithUUIDs = addUUIDs(incomingList);
            List<Map<String, Object>> existingList = getExistingContextData(userId, contextType);
            existingList.addAll(dataWithUUIDs);

            sortContextData(existingList, contextType);
            if (!saveContextData(userId, contextType, existingList)) {
                ProjectUtil.errorResponse(response, "Failed to save data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey("user:extendedProfile", contextType, userId), existingList);
            updateExtendedProfileAllCache(userId, contextType, existingList);
            savedDataWithUUIDs.addAll(dataWithUUIDs);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESULT, savedDataWithUUIDs);
        return response;
    }

    @Override
    public ApiResponse updateExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.update");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, "Invalid UserId in the request", HttpStatus.BAD_REQUEST);
            return response;
        }

        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);
            if (incomingList == null || incomingList.isEmpty())
                continue;

            List<Map<String, Object>> existingData = getExistingContextData(userId, contextType);
            Map<String, Map<String, Object>> dataMap = existingData.stream()
                    .filter(e -> e.get(Constants.UUID) != null)
                    .collect(Collectors.toMap(e -> (String) e.get(Constants.UUID), e -> e));

            for (Map<String, Object> item : incomingList) {
                String uuid = (String) item.get(Constants.UUID);
                if (uuid != null && dataMap.containsKey(uuid)) {
                    dataMap.get(uuid).putAll(item);
                } else {
                    ProjectUtil.errorResponse(response, "Invalid or missing UUID in incoming data.",
                            HttpStatus.BAD_REQUEST);
                    return response;
                }
            }

            List<Map<String, Object>> mergedList = new ArrayList<>(dataMap.values());
            sortContextData(mergedList, contextType);

            if (!saveContextData(userId, contextType, mergedList)) {
                ProjectUtil.errorResponse(response, "Failed to update data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey("user:extendedProfile", contextType, userId), mergedList);
            updateExtendedProfileAllCache(userId, contextType, mergedList);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    @Override
    public ApiResponse deleteExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.delete");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, "Invalid UserId in the request", HttpStatus.BAD_REQUEST);
            return response;
        }

        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> toDeleteList = (List<Map<String, Object>>) requestData.get(contextType);
            if (toDeleteList == null || toDeleteList.isEmpty())
                continue;

            Set<String> uuids = toDeleteList.stream()
                    .map(e -> (String) e.get(Constants.UUID))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<Map<String, Object>> existingData = getExistingContextData(userId, contextType);
            existingData.removeIf(e -> uuids.contains(e.get(Constants.UUID)));
            sortContextData(existingData, contextType);

            if (!saveContextData(userId, contextType, existingData)) {
                ProjectUtil.errorResponse(response, "Failed to delete data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey("user:extendedProfile", contextType, userId), existingData);
            updateExtendedProfileAllCache(userId, contextType, existingData);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    @Override
    public ApiResponse getExtendedProfileSummary(String userId, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.read");

        if (accessTokenValidator.fetchUserIdFromAccessToken(userToken) == null) {
            ProjectUtil.errorResponse(response, "Invalid UserId in the request", HttpStatus.BAD_REQUEST);
            return response;
        }

        String redisKey = buildCacheKey("user:extendedProfile", "all", userId);
        try {
            String cachedJson = cacheService.getCache(redisKey);
            if (cachedJson != null) {
                Map<String, Object> cachedResult = mapper.readValue(cachedJson, Map.class);
                response.setResponseCode(HttpStatus.OK);
                response.put(Constants.RESPONSE, cachedResult);
                return response;
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch summary from cache for userId {}: {}", userId, e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> data = getExistingContextData(userId, contextType);
            if (!data.isEmpty()) {
                Map<String, Object> contextSummary = new HashMap<>();
                contextSummary.put(Constants.COUNT, data.size());
                contextSummary.put(Constants.DATA, data.stream().limit(2).collect(Collectors.toList()));
                result.put(contextType, contextSummary);
            }
        }

        if (result.isEmpty()) {
            ProjectUtil.errorResponse(response, "No data found for user.", HttpStatus.NO_CONTENT);
            return response;
        }

        result.put(Constants.USERID_KEY, userId);
        try {
            cacheService.putCache(redisKey, mapper.writeValueAsString(result));
        } catch (Exception e) {
            logger.warn("Failed to cache extended profile summary for userId {}: {}", userId, e.getMessage());
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, result);
        return response;
    }

    @Override
    public ApiResponse readFullExtendedProfile(String userId, String contextType, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (userIdFromToken == null) {
            ProjectUtil.errorResponse(response, "Invalid UserId in the request", HttpStatus.BAD_REQUEST);
            return response;
        }

        String redisKey = buildCacheKey("user:extendedProfile", contextType, userId);
        List<Map<String, Object>> contextData = null;

        try {
            String cachedJson = cacheService.getCache(redisKey);
            if (cachedJson != null) {
                contextData = projectUtil.parseListOfMap(cachedJson);
            }
        } catch (Exception e) {
            logger.warn("Error reading from cache for key {}: {}", redisKey, e.getMessage());
        }

        if (contextData == null) {
            contextData = getExistingContextData(userId, contextType);
            if (contextData == null || contextData.isEmpty()) {
                ProjectUtil.errorResponse(response, "No data found for user.", HttpStatus.NO_CONTENT);
                return response;
            }
            try {
                cacheService.putCache(redisKey, mapper.writeValueAsString(contextData));
            } catch (Exception e) {
                logger.warn("Failed to cache data for key {}: {}", redisKey, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put(contextType, contextData);
        result.put(Constants.USER_ID_RQST, userId);
        result.put(Constants.COUNT, contextData.size());

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE,
                contextType.equalsIgnoreCase(Constants.LOCATION_DETAILS) ? contextData.get(0) : result);
        return response;
    }

    @Override
    public ApiResponse getBasicProfile(String userId, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.getBasicProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (userIdFromToken == null) {
            ProjectUtil.errorResponse(response, "Invalid or missing access token", HttpStatus.UNAUTHORIZED);
            return response;
        }

        boolean isSelfUser = userIdFromToken.equalsIgnoreCase(userId);
        String cacheKey = Constants.USER + ":basicProfile:" + userId;

        try {
            String cachedJson = cacheService.getCache(cacheKey);
            Map<String, Object> userProfile = (cachedJson != null)
                    ? mapper.readValue(cachedJson, Map.class)
                    : fetchFromDatabase(userId);

            if (userProfile == null) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.put(Constants.RESPONSE, Collections.emptyMap());
                return response;
            }

            double completion = calculateProfileCompletionPercentage(userProfile,
                    Constants.PROFILE_DETAILS_LOWERCASE, userId, userToken);
            userProfile.put("profileCompletion", completion);

            if (!isSelfUser) {
                sanitizeProfile(userProfile);
            }

            cacheService.putCache(cacheKey, mapper.writeValueAsString(userProfile));
            response.setResponse(userProfile);
        } catch (Exception e) {
            logger.error("Error fetching basic profile for userId: {}", userId, e);
            ProjectUtil.errorResponse(response, "Internal server error while fetching profile",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    // -------------------- HELPER METHODS --------------------

    private List<Map<String, Object>> addUUIDs(List<Map<String, Object>> list) {
        return list.stream().peek(item -> item.put(Constants.UUID, UUID.randomUUID().toString()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getExistingContextData(String userId, String contextType) {
        Map<String, Object> query = Map.of(Constants.USERID_KEY, userId, Constants.CONTEXT_TYPE, contextType);
        List<Map<String, Object>> rows = cassandraOperation.getRecordsByPropertiesByKey(Constants.DATABASE,
                Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);
        if (rows != null && !rows.isEmpty()) {
            String json = (String) rows.get(0).get(Constants.CONTEXT_DATA);
            try {
                return projectUtil.parseListOfMap(json);
            } catch (IOException e) {
                logger.error("Error parsing existing data for userId: {}, contextType: {}", userId, contextType);
            }
        }
        return new ArrayList<>();
    }

    private boolean saveContextData(String userId, String contextType, List<Map<String, Object>> dataList) {
        try {
            String finalJson = mapper.writeValueAsString(dataList);
            Map<String, Object> query = new HashMap<>();
            query.put(Constants.USERID_KEY, userId);
            query.put(Constants.CONTEXT_TYPE, contextType);
            query.put(Constants.CONTEXT_DATA, finalJson);
            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(Constants.DATABASE,
                    Constants.TABLE_USER_EXTENDED_PROFILE, query);
            return Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize context data for userId: {}, contextType: {}", userId, contextType);
        }
        return false;
    }

    private void sortContextData(List<Map<String, Object>> dataList, String contextType) {
        Comparator<Map<String, Object>> comparator = getSortingComparator(contextType);
        if (comparator != null) {
            dataList.sort(comparator.reversed());
        }
    }

    private Comparator<Map<String, Object>> getSortingComparator(String contextType) {
        return switch (contextType) {
            case Constants.SERVICE_HISTORY ->
                Comparator.comparing(map -> OffsetDateTime.parse((String) map.get(Constants.START_DATE)));
            case Constants.EDUCATIONAL_QUALIFICATIONS ->
                Comparator.comparing(map -> Integer.parseInt((String) map.get(Constants.START_YEAR)));
            case Constants.ACHIVEMENTS ->
                Comparator.comparing(map -> OffsetDateTime.parse((String) map.get(Constants.ISSUED_DATE)));
            default -> null;
        };
    }

    private String buildCacheKey(String prefix, String contextType, String userId) {
        return String.join(":", prefix, contextType, userId);
    }

    private void updateExtendedProfileAllCache(String userId, String contextType,
            List<Map<String, Object>> updatedContextData) {
        String allKey = "user:extendedProfile:all:" + userId;
        try {
            String allJson = cacheService.getCache(allKey);
            Map<String, List<Map<String, Object>>> allProfileData = (allJson != null && !allJson.isEmpty())
                    ? mapper.readValue(allJson, new TypeReference<>() {
                    })
                    : new HashMap<>();
            allProfileData.put(contextType, updatedContextData);
            cacheService.putCache(allKey, mapper.writeValueAsString(allProfileData));
        } catch (Exception e) {
            logger.error("Error updating extendedProfile all cache for userId {}: {}", userId, e.getMessage());
        }
    }

    private String validateUserExtendedProfileRequest(Map<String, Object> requestData) {
        if (requestData == null)
            return "Request data is missing.";
        List<String> errList = new ArrayList<>();
        validateFieldsForList(requestData, Constants.EDUCATIONAL_QUALIFICATIONS,
                serverConfig.getEducationalQualificationMandatoryFields(), errList);
        validateFieldsForList(requestData, Constants.ACHIVEMENTS, serverConfig.getAchievementsMandatoryFields(),
                errList);
        validateFieldsForList(requestData, Constants.SERVICE_HISTORY, serverConfig.getServiceHistoryMandatoryFields(),
                errList);
        return errList.isEmpty() ? "" : "Failed Due To Missing or Invalid Params - " + String.join(", ", errList) + ".";
    }

    private void validateFieldsForList(Map<String, Object> requestData, String listKey, String mandatoryFields,
            List<String> errList) {
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) requestData.get(listKey);
        if (dataList != null) {
            for (Map<String, Object> data : dataList) {
                String error = validateFields(data, mandatoryFields);
                if (!error.isEmpty()) {
                    errList.add(error);
                }
            }
        }
    }

    private String validateFields(Map<String, Object> data, String mandatoryFields) {
        StringBuilder errorMessages = new StringBuilder();
        for (String field : mandatoryFields.split(",")) {
            if (StringUtils.isBlank((String) data.get(field))) {
                errorMessages.append(field).append(" is mandatory. ");
            }
        }
        return errorMessages.toString();
    }

    private String validateRequestContextTypes(Map<String, Object> requestData, String[] contextTypes) {
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(contextTypes));
        allowedKeys.add(Constants.USER_ID_RQST);
        return requestData.keySet().stream()
                .filter(key -> !allowedKeys.contains(key))
                .findFirst()
                .map(key -> "Invalid context type in request: " + key)
                .orElse(null);
    }

    private Map<String, Object> fetchFromDatabase(String userId) {
        Map<String, Object> queryParams = Map.of(Constants.ID, userId);
        List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD, Constants.USER, queryParams, serverConfig.getBasicProfileFields(), null);

        if (records == null || records.isEmpty())
            return null;
        Map<String, Object> record = records.get(0);
        String profileDetailsJson = (String) record.get(Constants.PROFILE_DETAILS_LOWERCASE);

        try {
            if (profileDetailsJson != null) {
                Map<String, Object> profileDetailsMap = projectUtil.parseMap(profileDetailsJson);
                record.put(Constants.PROFILE_DETAILS_LOWERCASE, profileDetailsMap);
            }
        } catch (IOException e) {
            logger.warn("Invalid profileDetails JSON for userId: {}", userId, e);
            record.remove(Constants.PROFILE_DETAILS);
        }

        return record;
    }

    private void sanitizeProfile(Map<String, Object> profile) {
        Object detailsObj = profile.get(Constants.PROFILE_DETAILS_LOWERCASE);
        if (detailsObj instanceof Map<?, ?> detailsMap && detailsMap.containsKey(Constants.PERSONAL_DETAILS)) {
            detailsMap.remove(Constants.PERSONAL_DETAILS);
            logger.info("Removed personalDetails for non-self user.");
        }
    }

    protected double calculateProfileCompletionPercentage(Map<String, Object> profileData, String nestedFieldKey,
            String userId, String userToken) {
        List<String> requiredFields = serverConfig.getProfileCompletionRequiredFields();
        if (profileData == null || requiredFields == null || requiredFields.isEmpty())
            return 0.0;

        double totalCompletion = 0.0;
        Map<String, Object> nestedData = Optional.ofNullable(profileData.get(nestedFieldKey))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .orElse(Collections.emptyMap());

        for (String field : requiredFields) {
            boolean isFilled;
            try {
                if (isExtendedProfileField(field)) {
                    isFilled = hasExtendedProfileData(userId, field, userToken);
                } else {
                    Object value = profileData.getOrDefault(field, nestedData.get(field));
                    isFilled = value != null && !value.toString().trim().isEmpty();
                }
            } catch (Exception e) {
                logger.warn("Exception checking field '{}' for user '{}': {}", field, userId, e.getMessage());
                isFilled = false;
            }
            if (isFilled)
                totalCompletion += serverConfig.getFieldWeight();
        }

        return Math.min(100.0, Math.round(totalCompletion * 10.0) / 10.0);
    }

    private boolean isExtendedProfileField(String field) {
        return serverConfig.getExtendedFieldsConfig().stream()
                .anyMatch(f -> f.equalsIgnoreCase(field));
    }

    protected boolean hasExtendedProfileData(String userId, String contextType, String userToken) {
        try {
            ApiResponse response = readFullExtendedProfile(userId, contextType, userToken);
            if (response != null && response.getResponseCode() == HttpStatus.OK) {
                Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
                Object contextData = result.get(contextType);
                return contextData instanceof Collection && !((Collection<?>) contextData).isEmpty();
            }
        } catch (Exception e) {
            logger.error("Error checking extended profile data for userId {} and contextType {}: {}", userId,
                    contextType, e.getMessage());
        }
        return false;
    }
}
