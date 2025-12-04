package com.igot.cb.profile.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.igot.cb.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.igot.common.service.OutboundRequestHandlerServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.entity.CustomFieldEntity;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private AccessTokenValidator accessTokenValidator;
    private CbServerProperties serverConfig;
    private CassandraOperation cassandraOperation;
    private CacheService cacheService;
    private ObjectMapper mapper;
    private ProjectUtil projectUtil;
    private CustomFieldRepository customFieldRepository;
    private EsUtilServiceImpl esUtilService;
    
    private UserUtility userUtility;
    private UserInfoHelperServiceImpl userInfoHelperService;
    private final ProfileReaderServiceImpl profileReaderService;    

    public ProfileServiceImpl(
            AccessTokenValidator accessTokenValidator,
            CbServerProperties serverConfig,
            CassandraOperation cassandraOperation,
            CacheService cacheService,
            ObjectMapper mapper,
            ProjectUtil projectUtil,
            CustomFieldRepository customFieldRepository,
            EsUtilServiceImpl esUtilService,
            UserUtility userUtility,
            UserInfoHelperServiceImpl userInfoHelperService,
            ProfileReaderServiceImpl profileReaderService) {
        this.accessTokenValidator = accessTokenValidator;
        this.serverConfig = serverConfig;
        this.cassandraOperation = cassandraOperation;
        this.cacheService = cacheService;
        this.mapper = mapper;
        this.projectUtil = projectUtil;
        this.customFieldRepository = customFieldRepository;
        this.esUtilService = esUtilService;
        this.userUtility = userUtility;
        this.userInfoHelperService = userInfoHelperService;
        this.profileReaderService = profileReaderService;
    }

    // -------------------- Service METHODS --------------------

    @Override
    public ApiResponse saveExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.extendedProfile.create");
        
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken, response);
        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_USERID_ERROR_MSG, HttpStatus.BAD_REQUEST);
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
            List<Map<String, Object>> existingList = profileReaderService.getExistingContextData(userId, contextType);

            if(Constants.ACHIEVEMENTS.equalsIgnoreCase(contextType)) {
                mergeAndSortByIssuedDateOrTitle(existingList, dataWithUUIDs);
            }else{
                existingList.addAll(dataWithUUIDs);
            }

            if (!saveContextData(userId, contextType, existingList)) {
                ProjectUtil.errorResponse(response, "Failed to save data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, contextType, userId), existingList);
            updateExtendedProfileAllCache(userId, contextType, existingList);
            savedDataWithUUIDs.addAll(dataWithUUIDs);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESULT, savedDataWithUUIDs);
        return response;
    }

    @Override
    public ApiResponse updateExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.extendedProfile.update");
        
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken, response);
        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_USERID_ERROR_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);
            if (incomingList == null || incomingList.isEmpty())
                continue;

            List<Map<String, Object>> existingData = profileReaderService.getExistingContextData(userId, contextType);
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

            if (Constants.ACHIEVEMENTS.equalsIgnoreCase(contextType)) {
                mergeAndSortByIssuedDateOrTitle(mergedList, new ArrayList<>());
            }
            if (!saveContextData(userId, contextType, mergedList)) {
                ProjectUtil.errorResponse(response, "Failed to update data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, contextType, userId), mergedList);
            updateExtendedProfileAllCache(userId, contextType, mergedList);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    @Override
    public ApiResponse deleteExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.extendedProfile.delete");
        
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken, response);
        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_USERID_ERROR_MSG, HttpStatus.BAD_REQUEST);
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

            List<Map<String, Object>> existingData = profileReaderService.getExistingContextData(userId, contextType);
            existingData.removeIf(e -> uuids.contains(e.get(Constants.UUID)));

            if (!saveContextData(userId, contextType, existingData)) {
                ProjectUtil.errorResponse(response, "Failed to delete data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, contextType, userId), existingData);
            updateExtendedProfileAllCache(userId, contextType, existingData);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    @Override
    public ApiResponse getExtendedProfileSummary(String userId, String userToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.extendedProfile.read");

        if (accessTokenValidator.fetchUserIdFromAccessToken(userToken, response) == null) {
            ProjectUtil.errorResponse(response, Constants.INVALID_USERID_ERROR_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }       

        Map<String, Object> result = new HashMap<>();
        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> data = profileReaderService.readUserExtendedProfile(userId, contextType);
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

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, result);
        return response;
    }

    @Override
    public ApiResponse readFullExtendedProfile(String userId, String contextType, String userToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.extendedProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken, response);

        if (StringUtils.isBlank(userIdFromToken)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_USERID_ERROR_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        String redisKey = buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, contextType, userId);
        List<Map<String, Object>> contextData = null;

        try {
            String cachedJson = cacheService.getCache(redisKey);
            if (cachedJson != null) {
                contextData = projectUtil.parseListOfMap(cachedJson);
            }
        } catch (Exception e) {
            log.warn("Error reading from cache for key {}: {}", redisKey, e.getMessage());
        }

        if (contextData == null) {
            contextData = profileReaderService.getExistingContextData(userId, contextType);
            if (contextData == null || contextData.isEmpty()) {
                ProjectUtil.errorResponse(response, "No data found for user.", HttpStatus.NO_CONTENT);
                return response;
            }
            try {
                cacheService.putCache(redisKey, contextData);
            } catch (Exception e) {
                log.warn("Failed to cache data for key {}: {}", redisKey, e.getMessage());
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
        ApiResponse response = ApiResponse.createDefaultResponse("api.getBasicProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken, response);

        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        boolean isSelfUser = userIdFromToken.equalsIgnoreCase(userId);
        String cacheKey = Constants.USER + ":basicProfile:" + userId;

        try {
            String cachedJson = cacheService.getCache(cacheKey);
            Map<String, Object> userProfile;
            if (StringUtils.isNotEmpty(cachedJson)) {
                userProfile = mapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() {
                });
                List<String> cachedKeyList = new ArrayList<>(userProfile.keySet());
                List<String> differenceList = serverConfig.getBasicProfileFields().stream()
                        .filter(key -> !cachedKeyList.contains(key)).toList();
                if (!differenceList.isEmpty()) {
                    Map<String, Object> userDetails = profileReaderService.readUserDataFromDB(userId, differenceList);
                    if (MapUtils.isNotEmpty(userDetails)) {
                        userProfile.putAll(userDetails);
                    }
                }
            } else {
                userProfile = profileReaderService.readUserDataFromDB(userId, null);
            }
            userUtility.decryptSpecificUserData(userProfile, Arrays.asList(Constants.USERNAME_LOWERCASE));
            if (MapUtils.isEmpty(userProfile)) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.put(Constants.RESPONSE, Collections.emptyMap());
                return response;
            }
            userProfile.put(Constants.PROFILE_COMPLETION_PERCENTAGE, userInfoHelperService.calculateProfileCompletionPercentage(userProfile,
                    userId));
            userProfile.put(Constants.KARMA_POINTS, userInfoHelperService.getUserKarmaPoints(userId));
            userProfile.put(Constants.CERTIFICATE_COUNT, userInfoHelperService.getIssuedCertificateCount(userId));
            userProfile.put(Constants.POSTCOUNT, userInfoHelperService.getUserPostCount(userId));
            userProfile.put(Constants.ROLES, userInfoHelperService.getUserRoles(userId,(String)userProfile.get(Constants.ROOT_ORG_ID)));

            if (!isSelfUser) {
                userInfoHelperService.sanitizeProfile(userProfile, userToken);
            }

            Map<String,Object> responseMap = new HashMap<>();
            responseMap.put(Constants.RESPONSE, userProfile);
            response.setResponse(responseMap);
        } catch (Exception e) {
            log.error("Error fetching basic profile for userId: {}", userId, e);
            ProjectUtil.errorResponse(response, "Internal server error while fetching profile",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }


    // -------------------- HELPER METHODS --------------------

    private List<Map<String, Object>> addUUIDs(List<Map<String, Object>> list) {
        for (Map<String, Object> item : list) {
            item.put(Constants.UUID, UUID.randomUUID().toString());
        }
        return list;
    }

    private boolean saveContextData(String userId, String contextType, List<Map<String, Object>> dataList) {
        try {
            String finalJson = mapper.writeValueAsString(dataList);
            Map<String, Object> query = new HashMap<>();
            query.put(Constants.USERID_KEY, userId);
            query.put(Constants.CONTEXT_TYPE, contextType);
            query.put(Constants.CONTEXT_DATA, finalJson);
            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_EXTENDED_PROFILE, query);
            return Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize context data for userId: {}, contextType: {}", userId, contextType);
        }
        return false;
    }

    private String buildCacheKey(String prefix, String contextType, String userId) {
        return String.join(":", prefix, contextType, userId);
    }

    private void updateExtendedProfileAllCache(String userId, String contextType,
            List<Map<String, Object>> updatedContextData) {
        String allKey = Constants.USER_EXTENDED_PROFILE_ALL_PREFIX + userId;
        try {
            String allJson = cacheService.getCache(allKey);
            Map<String, Object> allProfileData = (allJson != null && !allJson.isEmpty())
                    ? mapper.readValue(allJson, new TypeReference<>() {
                    })
                    : new HashMap<>();
            Map<String, Object> updatedContext = new HashMap<>();
            updatedContext.put(Constants.DATA, updatedContextData);
            updatedContext.put(Constants.COUNT, updatedContextData != null ? updatedContextData.size() : 0);
            allProfileData.put(contextType, updatedContext);
            cacheService.putCache(allKey, allProfileData);
        } catch (Exception e) {
            log.error("Error updating extendedProfile all cache for userId {}: {}", userId, e.getMessage());
        }
    }

    private String validateUserExtendedProfileRequest(Map<String, Object> requestData) {
        if (requestData == null)
            return "Request data is missing.";
        List<String> errList = new ArrayList<>();
        validateFieldsForList(requestData, Constants.EDUCATIONAL_QUALIFICATIONS,
                serverConfig.getEducationalQualificationMandatoryFields(), errList, false);
        validateFieldsForList(requestData, Constants.ACHIVEMENTS, serverConfig.getAchievementsMandatoryFields(),
                errList, false);
        validateFieldsForList(requestData, Constants.SERVICE_HISTORY, serverConfig.getServiceHistoryMandatoryFields(),
                errList, true);
        return errList.isEmpty() ? "" : "Failed Due To Missing or Invalid Params - " + String.join(", ", errList) + ".";
    }

    private void validateFieldsForList(Map<String, Object> requestData, String listKey, String mandatoryFields,
            List<String> errList, boolean allowSkipEndDate) {
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) requestData.get(listKey);
        if (dataList != null) {
            for (Map<String, Object> data : dataList) {
                String error = validateFields(data, mandatoryFields, allowSkipEndDate);
                if (!error.isEmpty()) {
                    errList.add(error);
                }
            }
        }
    }

    private String validateFields(Map<String, Object> data, String mandatoryFields, boolean allowSkipEndDate) {
        StringBuilder errorMessages = new StringBuilder();
        for (String field : mandatoryFields.split(",")) {
            if (allowSkipEndDate && Constants.END_DATE.equals(field)) {
                Object currentlyWorking = data.get(Constants.CURRENTLY_WORKING);
                if (Constants.TRUE.equalsIgnoreCase(String.valueOf(currentlyWorking))) {
                    continue;
                }
            }
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

    private Map<String, Object> buildLimitedSummary(Map<String, Object> fullData) {
        Map<String, Object> limitedData = new HashMap<>();

        for (Map.Entry<String, Object> entry : fullData.entrySet()) {
            String key = entry.getKey();

            if (!(entry.getValue() instanceof Map)) {
                limitedData.put(key, entry.getValue());
                continue;
            }

            Map<String, Object> contextBlock = (Map<String, Object>) entry.getValue();
            Object dataObj = contextBlock.get(Constants.DATA);

            if (dataObj instanceof List) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                Map<String, Object> limitedBlock = new HashMap<>();
                limitedBlock.put(Constants.COUNT, contextBlock.get(Constants.COUNT));
                limitedBlock.put(Constants.DATA, dataList.size() > 2 ? dataList.subList(0, 2) : dataList);
                limitedData.put(key, limitedBlock);
            } else {
                limitedData.put(key, contextBlock);
            }
        }

        return limitedData;
    }

    private void mergeAndSortByIssuedDateOrTitle(List<Map<String, Object>> existingList, List<Map<String, Object>> newList) {
        List<Map<String, Object>> merged = Stream.concat(existingList.stream(), newList.stream())
                .sorted((a, b) -> {
                    OffsetDateTime dateA = parseOffsetDateTime(a.get(Constants.ISSUED_DATE));
                    OffsetDateTime dateB = parseOffsetDateTime(b.get(Constants.ISSUED_DATE));
                    if (dateA != null && dateB != null) {
                        return dateB.compareTo(dateA);
                    } else if (dateA == null && dateB == null) {
                        String titleA = (String) a.get(Constants.TITLE);
                        String titleB = (String) b.get(Constants.TITLE);
                        if (titleA == null && titleB == null) return 0;
                        if (titleA == null) return 1;
                        if (titleB == null) return -1;
                        return titleA.compareToIgnoreCase(titleB);
                    } else if (dateA == null) {
                        return 1;
                    } else {
                        return -1;
                    }
                })
                .toList();
        IntStream.range(0, merged.size()).forEach(i -> merged.get(i).put(Constants.INDEX, i));
        existingList.clear();
        existingList.addAll(merged);
    }

    private OffsetDateTime parseOffsetDateTime(Object dateObj) {
        if (dateObj instanceof String str && !str.isBlank()) {
            try {
                return OffsetDateTime.parse(str);
            } catch (Exception e) {
                log.debug("Failed to prase DateTime field: ", e);
            }
        }
        return null;
    }

    /**
     * Updates additional fields for a user in an organization
     */
    @Override
    public ApiResponse updateAdditionalFields(Map<String, Object> request, String authToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.update.additionalFields");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken, response);
        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        String validationError = validateAdditionalFieldsRequest(request);
        if (validationError != null) {
            ProjectUtil.errorResponse(response, validationError, HttpStatus.BAD_REQUEST);
            return response;
        }

        String userId = (String) request.get(Constants.USER_ID);
        String organisationId = (String) request.get(Constants.ORGANISATION_ID);
        List<Map<String, Object>> customFieldValues = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, "User ID in token does not match request", HttpStatus.UNAUTHORIZED);
            return response;
        }

        String contextType = Constants.ORG_ADDITIONAL_PROPERTIES;

        try {
            List<Map<String, Object>> existingData = profileReaderService.getExistingContextData(userId, contextType);

            List<Map<String, Object>> restructuredData = restructureByOrgId(existingData, organisationId, customFieldValues);

            if (!saveContextData(userId, contextType, restructuredData)) {
                ProjectUtil.errorResponse(response, "Failed to save additional fields", HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            // Transform for ES and update
            List<Map<String, Object>> esOrgCustomFields = transformOrgCustomFieldsForES(restructuredData);
            boolean updated = esUtilService.updateUserOrgCustomFields(userId, organisationId, esOrgCustomFields);

            if (!updated) {
                ProjectUtil.errorResponse(response, "Failed to update orgCustomFields in ES", HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            log.error("Error updating additional fields for userId: {}, orgId: {}", userId, organisationId, e);
            ProjectUtil.errorResponse(response, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Validates the request body for updating additional fields
     *
     * @param request The request body to validate
     * @return Error message if validation fails, null if validation passes
     */
    private String validateAdditionalFieldsRequest(Map<String, Object> request) {
        StringBuilder str = new StringBuilder();
        List<String> errList = new ArrayList<>();

        String userId = (String) request.get(Constants.USER_ID_RQST);
        if (StringUtils.isBlank(userId)) {
            errList.add(Constants.USER_ID_RQST);
        }

        String organisationId = (String) request.get(Constants.ORGANISATION_ID);
        if (StringUtils.isBlank(organisationId)) {
            errList.add(Constants.ORGANISATION_ID);
        }

        List<Map<String, Object>> customFieldValues = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);
        if (CollectionUtils.isEmpty(customFieldValues)) {
            errList.add(Constants.CUSTOM_FIELD_VALUES);
        }

        if (!errList.isEmpty()) {
            str.append(Constants.FAILED_DUE_TO_MISSING_PARAMS).append(errList).append(".");
            return str.toString();
        }

        for (Map<String, Object> field : customFieldValues) {
            String customFieldId = (String) field.get(Constants.CUSTOM_FIELD_ID);
            String fieldType = (String) field.get(Constants.FIELD_TYPE);

            if (StringUtils.isBlank(customFieldId)) {
                str.append("Each custom field must have a customFieldId. ");
                return str.toString();
            }

            if (StringUtils.isBlank(fieldType)) {
                str.append("Each custom field must have a type. ");
                return str.toString();
            }

            CustomFieldEntity customFieldEntity = getCustomFieldById(customFieldId);
            if (customFieldEntity == null) {
                str.append("Custom field with ID ").append(customFieldId).append(" does not exist. ");
                return str.toString();
            }

            if (!customFieldEntity.getIsActive()) {
                str.append("Custom field with ID ").append(customFieldId).append(" is not active. ");
                return str.toString();
            }

            String orgId = customFieldEntity.getCustomFieldData().get(Constants.ORGANISATION_ID).asText();
            if (!StringUtils.equals(orgId, organisationId)) {
                str.append(Constants.CUSTOM_FIELD).append(customFieldId)
                        .append(" is not configured for organization ").append(organisationId).append(". ");
                return str.toString();
            }

            String requestedAttributeName = (String) field.get(Constants.ATTRIBUTE_NAME);
            String actualAttributeName = customFieldEntity.getCustomFieldData().get(Constants.ATTRIBUTE_NAME).asText();
            if (!StringUtils.equals(requestedAttributeName, actualAttributeName)) {
                str.append("Invalid attribute name for custom field ").append(customFieldId).append(". ");
                return str.toString();
            }

            String storedType = customFieldEntity.getCustomFieldData().get(Constants.TYPE).asText();
            if (Constants.TEXT.equals(fieldType)) {
                if (field.get(Constants.VALUE) == null) {
                    str.append("Text field ").append(customFieldId).append(" must have a value. ");
                    return str.toString();
                }

                if (!Constants.TEXT.equals(storedType)) {
                    str.append(Constants.CUSTOM_FIELD).append(customFieldId).append(" is not of type text. ");
                    return str.toString();
                }
            } else if (Constants.MASTER_LIST.equals(fieldType)) {
                List<Map<String, Object>> values = (List<Map<String, Object>>) field.get(Constants.VALUES);
                if (CollectionUtils.isEmpty(values)) {
                    str.append("MasterList field ").append(customFieldId).append(" must have values. ");
                    return str.toString();
                }

                if (!Constants.MASTER_LIST.equals(storedType)) {
                    str.append(Constants.CUSTOM_FIELD).append(customFieldId).append(" is not of type masterList. ");
                    return str.toString();
                }

                String valueValidationError = validateMasterListValues(customFieldEntity, values);
                if (valueValidationError != null) {
                    str.append(valueValidationError);
                    return str.toString();
                }
            } else {
                str.append("Unsupported field type: ").append(fieldType).append(". ");
                return str.toString();
            }
        }
        return null;
    }

    /**
     * Validates the values for a masterList custom field
     *
     * @param entity          CustomFieldEntity containing the valid values
     * @param requestedValues Values from the request to validate
     * @return Error message if validation fails, null if validation passes
     */
    private String validateMasterListValues(CustomFieldEntity entity, List<Map<String, Object>> requestedValues) {
        try {
            JsonNode customFieldData = entity.getCustomFieldData().get(Constants.CUSTOM_FIELD_DATA);
            if (customFieldData == null || !customFieldData.isArray()) {
                return "Invalid master list field definition.";
            }

            // Check for duplicate levels - only one entry per level is allowed
            Map<Integer, Integer> levelCounts = new HashMap<>();
            for (Map<String, Object> value : requestedValues) {
                Integer level = (Integer) value.get(Constants.LEVEL);
                if (level == null) {
                    return "Each master list value must have a level.";
                }

                levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
                if (levelCounts.get(level) > 1) {
                    return "Only one value allowed per level. Found multiple entries at level " + level;
                }
            }

            // Sort values by level to validate parent-child relationships
            List<Map<String, Object>> sortedValues = requestedValues.stream()
                    .sorted(Comparator.comparing(map -> (Integer) map.get(Constants.LEVEL)))
                    .collect(Collectors.toList());

            // Track parent node for hierarchical validation
            JsonNode currentParentNode = null;

            // Validate each value in the hierarchy
            for (Map<String, Object> value : sortedValues) {
                String attributeName = (String) value.get(Constants.ATTRIBUTE_NAME);
                String valueStr = String.valueOf(value.get(Constants.VALUE));
                Integer level = (Integer) value.get(Constants.LEVEL);

                if (StringUtils.isBlank(attributeName) || valueStr == null || level == null) {
                    return "Each master list value must have attribute name, value and level.";
                }

                // For level 1, find matching node by value
                if (level == 1) {
                    currentParentNode = null;
                    for (JsonNode node : customFieldData) {
                        if (node.has(Constants.FIELD_VALUE) && valueStr.equals(node.get(Constants.FIELD_VALUE).asText())) {
                            currentParentNode = node;
                            break;
                        }
                    }

                    if (currentParentNode == null) {
                        return "Invalid value '" + valueStr + "' at level 1";
                    }
                }
                // For higher levels, find in children of current parent by value
                else if (currentParentNode != null) {
                    JsonNode childValues = currentParentNode.get(Constants.FIELD_VALUES);
                    JsonNode nextParent = null;

                    if (childValues != null && childValues.isArray()) {
                        for (JsonNode childNode : childValues) {
                            if (childNode.has(Constants.FIELD_VALUE) &&
                                    valueStr.equals(childNode.get(Constants.FIELD_VALUE).asText())) {
                                nextParent = childNode;
                                break;
                            }
                        }
                    }

                    if (nextParent == null) {
                        return "Invalid value '" + valueStr + "' at level " + level +
                                ". Not found under parent '" + currentParentNode.get(Constants.FIELD_VALUE).asText() + "'";
                    }

                    currentParentNode = nextParent;
                } else {
                    return "Invalid hierarchy structure. Parent node not found for level " + level;
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Error validating master list values: {}", e.getMessage());
            return "Error validating master list values.";
        }
    }

    /**
     * Restructures data to group by organization ID
     */
    private List<Map<String, Object>> restructureByOrgId(List<Map<String, Object>> existingData,
                                                         String currentOrgId,
                                                         List<Map<String, Object>> newCustomFieldValues) {

        Map<String, List<Map<String, Object>>> orgMap = new HashMap<>();
        for (Map<String, Object> item : existingData) {
            if (item.containsKey(Constants.ORGANISATION_ID) && item.containsKey(Constants.CUSTOM_FIELD_VALUES)) {
                String orgId = (String) item.get(Constants.ORGANISATION_ID);
                orgMap.put(orgId, (List<Map<String, Object>>) item.get(Constants.CUSTOM_FIELD_VALUES));
            } else if (item.containsKey(Constants.ORGANISATION_ID)) {
                String orgId = (String) item.get(Constants.ORGANISATION_ID);
                orgMap.computeIfAbsent(orgId, k -> new ArrayList<>()).add(item);
            }
        }
        orgMap.put(currentOrgId, newCustomFieldValues);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : orgMap.entrySet()) {
            Map<String, Object> orgData = new HashMap<>();
            orgData.put(Constants.ORGANISATION_ID, entry.getKey());
            orgData.put(Constants.CUSTOM_FIELD_VALUES, entry.getValue());
            result.add(orgData);
        }
        return result;
    }

    /**
     * Retrieves a custom field from PostgreSQL by its ID
     *
     * @param customFieldId ID of the custom field to retrieve
     * @return CustomFieldEntity if found, null otherwise
     */
    private CustomFieldEntity getCustomFieldById(String customFieldId) {
        try {
            return customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId).orElse(null);
        } catch (Exception e) {
            log.error("Error retrieving custom field with ID {}: {}", customFieldId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public ApiResponse getAdditionalFieldsByOrg(String userId, String orgId, String authToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.get.additionalFieldsByOrg");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken, response);
        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, "User ID in token does not match request", HttpStatus.UNAUTHORIZED);
            return response;
        }

        try {
            String contextType = Constants.ORG_ADDITIONAL_PROPERTIES;
            List<Map<String, Object>> dataList = profileReaderService.getExistingContextData(userId, contextType);

            // Find data for the specified organization
            Map<String, Object> orgData = null;
            for (Map<String, Object> item : dataList) {
                String itemOrgId = (String) item.get(Constants.ORGANISATION_ID);
                if (orgId.equals(itemOrgId)) {
                    orgData = item;
                    break;
                }
            }

            if (MapUtils.isEmpty(orgData)) {
                response.setResponseCode(HttpStatus.OK);
                response.put(Constants.RESPONSE, Collections.emptyMap());
                return response;
            }
            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESPONSE, orgData);
            return response;
        } catch (Exception e) {
            log.error("Error retrieving additional fields for userId: {} and orgId: {}", userId, orgId, e);
            ProjectUtil.errorResponse(response, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
    }

    private List<Map<String, Object>> transformOrgCustomFieldsForES(List<Map<String, Object>> restructuredData) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> orgEntry : restructuredData) {
            String orgId = (String) orgEntry.get(Constants.ORGANISATION_ID);
            List<Map<String, Object>> customFieldValues = (List<Map<String, Object>>) orgEntry.get(Constants.CUSTOM_FIELD_VALUES);
            List<Map<String, Object>> fields = new ArrayList<>();

            for (Map<String, Object> field : customFieldValues) {
                String type = (String) field.get(Constants.FIELD_TYPE);
                if (Constants.MASTER_LIST.equals(type)) {
                    List<Map<String, Object>> values = (List<Map<String, Object>>) field.get(Constants.VALUES);
                    for (Map<String, Object> value : values) {
                        String attr = (String) value.get(Constants.ATTRIBUTE_NAME);
                        Object val = value.get(Constants.VALUE);
                        fields.add(Map.of(attr, val));
                    }
                } else if (Constants.TEXT.equals(type)) {
                    String attr = (String) field.get(Constants.ATTRIBUTE_NAME);
                    Object val = field.get(Constants.VALUE);
                    fields.add(Map.of(attr, val));
                }
            }

            Map<String, Object> orgFields = new HashMap<>();
            orgFields.put(Constants.ORG_ID, orgId);
            orgFields.put(Constants.FIELDS, fields);
            result.add(orgFields);
        }
        return result;
    }
}
