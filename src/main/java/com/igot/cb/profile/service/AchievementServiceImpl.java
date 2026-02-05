package com.igot.cb.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
public class AchievementServiceImpl implements AchievementService{

    private List<String> requiredFields;

    private final AccessTokenValidator accessTokenValidator;

    private final CbServerProperties cbServerProperties;

    private final CassandraOperation cassandraOperation;

    private final EsClientService esClientService;

    private final ObjectMapper objectMapper;

    private static final String FIELD_REASON = "reason";

    private static final String FIELD_LEARNER_ID = "learnerId";

    @Autowired
    private CacheService cacheService;

    @Autowired
    public AchievementServiceImpl(
            AccessTokenValidator accessTokenValidator,
            CbServerProperties cbServerProperties,
            CassandraOperation cassandraOperation,
            EsClientService esClientService,
            ObjectMapper objectMapper) {
        this.accessTokenValidator = accessTokenValidator;
        this.cbServerProperties = cbServerProperties;
        this.cassandraOperation = cassandraOperation;
        this.esClientService = esClientService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void initRequiredFields() {
        requiredFields = Arrays.asList(cbServerProperties.getRequiredFieldsProperty().split(","));
    }

    @Override
    public ApiResponse createLearnerAchievement(Map<String, Object> request, String userToken, String orgId) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.create");

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isEmpty(userId)) {
            ProjectUtil.errorResponse(response, "UserId not Found", HttpStatus.BAD_REQUEST);
            return response;
        }
        String validationError = validateRequetData(requestData);
        if (StringUtils.isNotBlank(validationError)) {
            ProjectUtil.errorResponse(response, validationError, HttpStatus.BAD_REQUEST);
            return response;
        }
        String contextType = (String) requestData.get(Constants.CONTEXT_TYPE);
        String source = (String) requestData.get(Constants.SOURCE);
        Map<String, Object> contextData =
                (Map<String, Object>) requestData.get(Constants.CONTEXT_DATA);

        String id = UUID.randomUUID().toString();
        LocalDate createdOn = LocalDate.now();

        Map<String, Object> achievementRecord = new HashMap<>();
        achievementRecord.put(Constants.USER_ID_RQST, userId);
        achievementRecord.put(Constants.CONTEXT_TYPE, contextType);
        achievementRecord.put(Constants.ID, id);
        achievementRecord.put(Constants.ORG_ID, orgId);
        achievementRecord.put(Constants.SOURCE, source);
        achievementRecord.put(Constants.CONTEXT_DATA, contextData);
        achievementRecord.put(Constants.STATUS, Constants.PENDING);
        achievementRecord.put(Constants.CREATED_ON, createdOn);

        // Save into Cassandra
        boolean isSaved = saveAchievementToCassandra(achievementRecord);
        if (!isSaved) {
            ProjectUtil.errorResponse(response,
                    "Failed to save learner achievement info",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
        Map<String, Object> esRecord = new HashMap<>(achievementRecord);
        esRecord.put(Constants.CREATED_ON, createdOn.toString());
        Map<String, Object> map = objectMapper.convertValue(esRecord, Map.class);
        esClientService.addDocument(Constants.LEARNER_ACHIEVEMENT_INDEX, Constants.INDEX_TYPE, id, map, cbServerProperties.getAchievementEsRequiredFieldsMappingPath());

        // Cache record
        cacheService.putCache(
                buildCacheKey("user:achievement", userId, contextType, id),
                esRecord
        );
        response.setResponseCode(HttpStatus.OK);
        response.setResponse(achievementRecord);
        return response;
    }

    @Override
    public ApiResponse updateLearnerAchievement(Map<String, Object> request, String userToken, String orgId) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.update");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String validateMessage = validateRequetData(requestData);
        if (StringUtils.isNotBlank(validateMessage)) {
            ProjectUtil.errorResponse(response, validateMessage, HttpStatus.BAD_REQUEST);
            return response;
        }
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isEmpty(userId)) {
            ProjectUtil.errorResponse(response, "UserId not Found", HttpStatus.BAD_REQUEST);
            return response;
        }
        // Validate mandatory fields
        String id = (String) requestData.get(Constants.ID);
        String contextType = (String) requestData.get(Constants.CONTEXT_TYPE);

        if (StringUtils.isBlank(id) || StringUtils.isBlank(contextType)) {
            ProjectUtil.errorResponse(response, "id and contextType are mandatory", HttpStatus.BAD_REQUEST);
            return response;
        }
        Map<String, Object> newContextData =
                (Map<String, Object>) requestData.get(Constants.CONTEXT_DATA);

        if (MapUtils.isEmpty(newContextData)) {
            ProjectUtil.errorResponse(response, "contextData is mandatory for update", HttpStatus.BAD_REQUEST);
            return response;
        }

        //  Fetch existing record
        Map<String, Object> existingRecord =
                getAchievementFromCassandra(userId, contextType, id);

        if (existingRecord == null) {
            ProjectUtil.errorResponse(response, "Achievement records not found", HttpStatus.NOT_FOUND);
            return response;
        }

        String currentStatus = (String) existingRecord.get(Constants.STATUS);

        if (!Constants.PENDING.equalsIgnoreCase(currentStatus)) {
            ProjectUtil.errorResponse(response,
                    "Only PENDING achievements can be updated",
                    HttpStatus.BAD_REQUEST);
            return response;
        }
        existingRecord.put(Constants.CONTEXT_DATA, newContextData);
        LocalDate updateOn = LocalDate.now();
        existingRecord.put(Constants.UPDATED_ON, updateOn);
        existingRecord.put(Constants.UPDATED_BY, userId);

        boolean isSaved = saveAchievementToCassandra(existingRecord);
        if (!isSaved) {
            ProjectUtil.errorResponse(response,
                    "Failed to update learner achievement",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
        Map<String, Object> esRecord = new HashMap<>(existingRecord);
        esRecord.put(Constants.CREATED_ON, existingRecord.get(Constants.CREATED_ON).toString());
        esRecord.put(Constants.UPDATED_ON, updateOn.toString());
        Map<String, Object> map = objectMapper.convertValue(esRecord, Map.class);
        esClientService.updateDocument(Constants.LEARNER_ACHIEVEMENT_INDEX, Constants.INDEX_TYPE, id, map, cbServerProperties.getAchievementEsRequiredFieldsMappingPath());
        cacheService.putCache(
                buildCacheKey("user:achievement", userId, contextType, id),
                esRecord
        );
        response.setResponseCode(HttpStatus.OK);
        response.setResponse(esRecord);
        return response;
    }

    @Override
    public ApiResponse readLearnerAchievement(String achievementId, String userToken, String contextType) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.read");
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, "UserId not Found", HttpStatus.BAD_REQUEST);
            return response;
        }
        if (StringUtils.isBlank(achievementId)) {
            ProjectUtil.errorResponse(response, "achievementId is mandatory", HttpStatus.BAD_REQUEST);
            return response;
        }
        Map<String, Object> achievement = null;
        if (StringUtils.isNotBlank(contextType)) {
            String cacheKey = buildCacheKey("user:achievement", userId, contextType, achievementId);
            achievement = getAchievementFromCache(cacheKey);
        }
        if (MapUtils.isEmpty(achievement)) {
            achievement = getAndCacheAchievementFromCassandra(userId, contextType, achievementId);
        }
        if (MapUtils.isEmpty(achievement)) {
            ProjectUtil.errorResponse(response, "Achievement not found", HttpStatus.NOT_FOUND);
            return response;
        }

        response.setResponseCode(HttpStatus.OK);
        response.setResponse(achievement);
        return response;
    }

    @Override
    public ApiResponse deleteLearnerAchievement(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.delete");
        if (request == null || !(request.get(Constants.REQUEST) instanceof Map)) {
            ProjectUtil.errorResponse(response, "Missing or invalid 'request' object in payload", HttpStatus.BAD_REQUEST);
            return response;
        }
        Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
        String achievementId = (String) reqMap.get(Constants.ID);
        String contextType = (String) reqMap.get(Constants.CONTEXT_TYPE);
        if (StringUtils.isBlank(achievementId) || StringUtils.isBlank(contextType)) {
            ProjectUtil.errorResponse(response, "achievementId and contextType are mandatory", HttpStatus.BAD_REQUEST);
            return response;
        }
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, "UserId not Found", HttpStatus.BAD_REQUEST);
            return response;
        }
        // Delete from Cassandra
        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put(Constants.ID, achievementId);
        compositeKey.put(Constants.USER_ID_RQST, userId);
        compositeKey.put(Constants.CONTEXT_TYPE, contextType);
        Map<String, Object> cassandraResponse = cassandraOperation.deleteRecordByCompositeKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                compositeKey
        );
        if (!Constants.SUCCESS.equals(cassandraResponse.get(Constants.RESPONSE))) {
            ProjectUtil.errorResponse(response, "Failed to delete achievement record", HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
        // Remove from cache
        String cacheKey = buildCacheKey("user:achievement", userId, contextType, achievementId);
        cacheService.removeCache(cacheKey);
        // Remove from ES
        try {
            esClientService.deleteDocument(achievementId, Constants.LEARNER_ACHIEVEMENT_INDEX);
        } catch (Exception e) {
            log.warn("Failed to delete achievement from ES for id {}", achievementId, e);
        }
        response.setResponseCode(HttpStatus.OK);
        response.getResult().put("message", "Achievement deleted successfully");
        return response;
    }

    @Override
    public ApiResponse statusUpdateLearnerAchievement(Map<String, Object> request, String authToken) {
        log.info("AchievementService::statusUpdateLearnerAchievement");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_STATUS_UPDATE);
        try {
            String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isBlank(userIdFromToken)) {
                ProjectUtil.errorResponse(response, "Invalid or missing access token", HttpStatus.UNAUTHORIZED);
                return response;
            }
            if (!validateStatusUpdateRequest(request, response)) {
                return response;
            }
            Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
            Map<String, Object> compositeKey = new HashMap<>();
            compositeKey.put(Constants.ID, reqMap.get(Constants.ID));
            compositeKey.put(Constants.USER_ID, reqMap.get(FIELD_LEARNER_ID));
            compositeKey.put(Constants.FIELD_CONTEXT_TYPE, reqMap.get(Constants.CONTENT_TYPE_KEY));
            List<Map<String, Object>> records = cassandraOperation.getAllRecordsByPrimaryKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                compositeKey,
                null,
                Constants.CASSANDRA_FETCH_LIMIT
            );
            if (CollectionUtils.isEmpty(records)) {
                ProjectUtil.errorResponse(response, "Achievement record not found for update", HttpStatus.NOT_FOUND);
                return response;
            }
            // Additional validation: status must be PENDING
            String currentStatus = String.valueOf(records.get(0).get(Constants.STATUS));
            if (!Constants.PENDING.equalsIgnoreCase(currentStatus)) {
                ProjectUtil.errorResponse(response, "Achievement status must be 'PENDING' to update. Current status: " + currentStatus, HttpStatus.BAD_REQUEST);
                return response;
            }
            Map<String, Object> updateAttributes = new HashMap<>();
            updateAttributes.put(Constants.STATUS, reqMap.get(Constants.STATUS));
            updateAttributes.put(FIELD_REASON, reqMap.get(FIELD_REASON));
            updateAttributes.put(Constants.FIELD_APPROVED_BY, userIdFromToken);
            // Store approvedon as date (yyyy-MM-dd) for Cassandra
            String approvedOnDate = java.time.LocalDate.now().toString();
            updateAttributes.put(Constants.FIELD_APPROVED_ON, approvedOnDate);
            Map<String, Object> cassandraResponse = cassandraOperation.updateRecordByCompositeKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                updateAttributes,
                compositeKey
            );
            if (!Constants.SUCCESS.equals(cassandraResponse.get(Constants.RESPONSE))) {
                ProjectUtil.errorResponse(response, String.valueOf(cassandraResponse.get(Constants.ERROR_MESSAGE)), HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }
            updateAchievementInES(records, reqMap, userIdFromToken, approvedOnDate);
            response.getResult().put("message", "Achievement status updated successfully");
        } catch (Exception e) {
            log.error("Exception in statusUpdateLearnerAchievement", e);
            ProjectUtil.errorResponse(response, "Exception occurred: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private void updateAchievementInES(List<Map<String, Object>> records, Map<String, Object> reqMap, String userIdFromToken, String approvedOnDate) {
        try {
            Map<String, Object> esUpdateMap = new HashMap<>();
            if (CollectionUtils.isNotEmpty(records)) {
                Map<String, Object> dbRecord = records.get(0);
                for (Map.Entry<String, Object> entry : dbRecord.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof java.time.LocalDate) {
                        esUpdateMap.put(entry.getKey(), value.toString());
                    } else if (value instanceof java.time.LocalDateTime) {
                        esUpdateMap.put(entry.getKey(), value.toString());
                    } else if ("contextdata".equalsIgnoreCase(entry.getKey()) && value != null) {
                        if (value instanceof String) {
                            try {
                                Map<String, Object> contextDataMap = objectMapper.readValue((String) value, Map.class);
                                esUpdateMap.put(entry.getKey(), contextDataMap);
                            } catch (Exception ex) {
                                log.warn("Failed to parse contextData string to Map for ES. Storing as empty object.", ex);
                                esUpdateMap.put(entry.getKey(), new HashMap<>());
                            }
                        } else if (value instanceof Map) {
                            esUpdateMap.put(entry.getKey(), value);
                        } else {
                            esUpdateMap.put(entry.getKey(), new HashMap<>());
                        }
                    } else {
                        esUpdateMap.put(entry.getKey(), value);
                    }
                }
            }
            esUpdateMap.put(Constants.STATUS, reqMap.get(Constants.STATUS));
            esUpdateMap.put(FIELD_REASON, reqMap.get(FIELD_REASON));
            esUpdateMap.put(Constants.FIELD_APPROVED_BY, userIdFromToken);
            esUpdateMap.put(Constants.FIELD_APPROVED_ON, approvedOnDate);
            esClientService.updateDocument(
                Constants.LEARNER_ACHIEVEMENT_INDEX,
                null,
                String.valueOf(reqMap.get(Constants.ID)),
                esUpdateMap,
                cbServerProperties.getAchievementEsRequiredFieldsMappingPath()
            );
        } catch (Exception e) {
            log.error("Exception while updating achievement in ES", e);
        }
    }

    private boolean validateStatusUpdateRequest(Map<String, Object> request, ApiResponse response) {
        if (MapUtils.isEmpty(request) || !(request.get(Constants.REQUEST) instanceof Map) || MapUtils.isEmpty((Map<?, ?>) request.get(Constants.REQUEST))) {
            ProjectUtil.errorResponse(response, "Missing or invalid 'request' object in payload", HttpStatus.BAD_REQUEST);
            return false;
        }
        Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
        for (String field : requiredFields) {
            if (!reqMap.containsKey(field) || reqMap.get(field) == null) {
                ProjectUtil.errorResponse(response, "Missing required field: " + field, HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        String statusValue = String.valueOf(reqMap.get(Constants.STATUS));
        if (!Constants.APPROVED.equalsIgnoreCase(statusValue) && !Constants.REJECT.equalsIgnoreCase(statusValue)) {
            ProjectUtil.errorResponse(response, "Invalid status value. Allowed values are 'Approved' or 'Reject'", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    private String validateRequetData(Map<String, Object> requestData) {
        String requestContextType = (String) requestData.get(Constants.CONTEXT_TYPE);
        String[] configuredContextType = cbServerProperties.getContextType();
        if (StringUtils.isBlank(requestContextType)) {
            return "contextType is missing in request";
        }

        boolean isValid = Arrays.stream(configuredContextType)
                .anyMatch(ct -> ct.equalsIgnoreCase(requestContextType));
        if (!isValid) {
            return "Invalid contextType. Allowed values: " + String.join(",", configuredContextType);
        }
        Map<String, Object> contextData =
                (Map<String, Object>) requestData.get(Constants.CONTEXT_DATA);

        if (MapUtils.isEmpty(contextData)|| contextData.isEmpty()) {
            return "contextData is missing in request";
        }
        String requiredFieldsConfig = cbServerProperties.getAchievementsMandatoryFields();
        String[] requiredFields = requiredFieldsConfig.split(",");

        //  Validate mandatory fields
        for (String field : requiredFields) {
            Object value = contextData.get(field.trim());
            if (value == null) {
                return field + " is mandatory and missing";
            }
            if (value instanceof String && StringUtils.isBlank((String) value)) {
                return field + " is mandatory and cannot be empty";
            }
        }
        return null;
    }

    private boolean saveAchievementToCassandra(Map<String, Object> achievementRecord) {
        try {
            Map<String, Object> query = new HashMap<>(achievementRecord);
            String contextDataJson = objectMapper.writeValueAsString(
                    achievementRecord.get(Constants.CONTEXT_DATA)
            );
            query.put(Constants.CONTEXT_DATA, contextDataJson);

            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.LEARNER_ACHIEVEMENT_TABLE,
                    query
            );
            return Constants.SUCCESS.equalsIgnoreCase(
                    (String) insertResponse.get(Constants.RESPONSE)
            );
        } catch (Exception e) {
            log.error("Failed to insert learner achievement", e);
            return false;
        }
    }

    private String buildCacheKey(String prefix, String userId, String contextType, String id) {
        return String.join(":", prefix, contextType, userId, id);
    }

    private Map<String, Object> getAchievementFromCassandra(String userId, String contextType, String id) {
        try {
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USER_ID_RQST, userId);
            propertyMap.put(Constants.CONTEXT_TYPE, contextType);
            propertyMap.put(Constants.ID, id);
            List<String> fields = new ArrayList<>();

            List<Map<String, Object>> result =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            Constants.KEYSPACE_SUNBIRD,
                            Constants.LEARNER_ACHIEVEMENT_TABLE,
                            propertyMap,
                            fields,
                            Constants.USERID_KEY
                    );
            if (result == null || result.isEmpty()) {
                return null;
            }
            Map<String, Object> record = result.get(0);

            // Convert contextdata JSON string back to Map
            Object contextDataObj = record.get(Constants.CONTEXT_DATA);
            if (contextDataObj instanceof String) {
                Map<String, Object> contextData =
                        objectMapper.readValue((String) contextDataObj, Map.class);
                record.put(Constants.CONTEXT_DATA, contextData);
            }
            return record;

        } catch (Exception e) {
            log.error("Failed to fetch learner achievement for userId={}, contextType={}, id={}",
                    userId, contextType, id, e);
            return null;
        }
    }

    private Map<String, Object> getAchievementFromCache(String cacheKey) {
        String cachedJson = cacheService.getCache(cacheKey);
        if (StringUtils.isNotBlank(cachedJson)) {
            try {
                log.info("reading from cache {}", cacheKey);
                return objectMapper.readValue(cachedJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                log.error("Failed to deserialize cached achievement for key {}", cacheKey, e);
            }
        }
        return null;
    }

    private Map<String, Object> getAndCacheAchievementFromCassandra(String userId, String contextType, String achievementId) {
        Map<String, Object> achievement = getAchievementFromCassandra(userId, contextType, achievementId);
        if (achievement != null && StringUtils.isNotBlank(contextType)) {
            try {
                String achievementJson = objectMapper.writeValueAsString(achievement);
                cacheService.putCache(buildCacheKey("user:achievement", userId, contextType, achievementId), achievementJson);
            } catch (Exception e) {
                log.error("Failed to serialize achievement for caching", e);
            }
        }
        return achievement;
    }
}
