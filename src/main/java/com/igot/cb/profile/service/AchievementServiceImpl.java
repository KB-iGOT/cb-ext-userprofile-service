package com.igot.cb.profile.service;

import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AchievementServiceImpl implements AchievementService{

    @Value("${achievement.status.update.required.fields}")
    private String requiredFieldsProperty;
    private List<String> requiredFields;

    @Autowired
    private AccessTokenValidator accessTokenValidator;

    @Autowired
    private CassandraOperation cassandraOperation;

    @Autowired
    private EsClientService esClientService;
    @Value("${elastic.required.field.achievement.json.path}")
    private String achievementEsRequiredFieldsMappingPath;

    private static final String FIELD_REASON = "reason";
    private static final String FIELD_LEARNER_ID = "learnerId";


    @PostConstruct
    private void initRequiredFields() {
        requiredFields = Arrays.asList(requiredFieldsProperty.split(","));
    }

    @Override
    public ApiResponse createLearnerAchievement(Map<String, Object> request, String userToken) {
        return null;
    }

    @Override
    public ApiResponse updateLearnerAchievement(Map<String, Object> request, String userToken) {
        return null;
    }

    @Override
    public ApiResponse deleteLearnerAchievement(Map<String, Object> request, String userToken) {
        return null;
    }

    @Override
    public ApiResponse readLearnerAchievement(String achievementId, String userToken) {
        return null;
    }

    @Override
    public ApiResponse statusUpdateLearnerAchievement(Map<String, Object> request, String authToken) {
        log.info("AchievementService::statusUpdateLearnerAchievement");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_STATUS_UPDATE);
        try {
            String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (userIdFromToken == null) {
                ProjectUtil.errorResponse(response, "Invalid or missing access token", HttpStatus.UNAUTHORIZED);
                return response;
            }
            if (!validateStatusUpdateRequest(request, response)) {
                return response;
            }
            Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
            // Check if record exists
            Map<String, Object> compositeKey = new HashMap<>();
            compositeKey.put(Constants.ID, reqMap.get(Constants.ID));
            compositeKey.put(Constants.USER_ID, reqMap.get(FIELD_LEARNER_ID));
            compositeKey.put(Constants.FIELD_CONTEXT_TYPE, Constants.CONTEXT_TYPE_ACHIEVEMENTS);
            List<Map<String, Object>> records = cassandraOperation.getAllRecordsByPrimaryKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                compositeKey,
                null,
                Constants.CASSANDRA_FETCH_LIMIT
            );
            if (records == null || records.isEmpty()) {
                ProjectUtil.errorResponse(response, "Achievement record not found for update", HttpStatus.NOT_FOUND);
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
            // Prepare ES update map (partial update)
            Map<String, Object> esUpdateMap = new HashMap<>();
            // Add all existing DB fields to ES map, converting LocalDate/LocalDateTime to String
            if (!records.isEmpty()) {
                Map<String, Object> dbRecord = records.get(0);
                for (Map.Entry<String, Object> entry : dbRecord.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof java.time.LocalDate) {
                        esUpdateMap.put(entry.getKey(), value.toString());
                    } else if (value instanceof java.time.LocalDateTime) {
                        esUpdateMap.put(entry.getKey(), value.toString());
                    } else if ("contextdata".equalsIgnoreCase(entry.getKey()) && value != null) {
                        // Ensure contextData is always a Map for ES mapping
                        if (value instanceof String) {
                            try {
                                // Try to parse JSON string to Map
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                Map<String, Object> contextDataMap = mapper.readValue((String) value, Map.class);
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
            // Overwrite/add new attributes
            esUpdateMap.put(Constants.STATUS, reqMap.get(Constants.STATUS));
            esUpdateMap.put(FIELD_REASON, reqMap.get(FIELD_REASON));
            esUpdateMap.put(Constants.FIELD_APPROVED_BY, userIdFromToken);
            esUpdateMap.put(Constants.FIELD_APPROVED_ON, approvedOnDate); // ES mapping expects date
            // Update ES index (do not pass type at all for ES 7+)
            esClientService.updateDocument(
                "achievement_entity",
                null, // pass null for type so it is omitted in the request
                String.valueOf(reqMap.get(Constants.ID)),
                esUpdateMap,
                achievementEsRequiredFieldsMappingPath
            );
            response.getResult().put("message", "Achievement status updated successfully");
        } catch (Exception e) {
            log.error("Exception in statusUpdateLearnerAchievement", e);
            ProjectUtil.errorResponse(response, "Exception occurred: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private boolean validateStatusUpdateRequest(Map<String, Object> request, ApiResponse response) {
        if (request == null || !request.containsKey(Constants.REQUEST) || !(request.get(Constants.REQUEST) instanceof Map)) {
            ProjectUtil.errorResponse(response, "Missing or invalid 'request' object in payload", org.springframework.http.HttpStatus.BAD_REQUEST);
            return false;
        }
        Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
        for (String field : requiredFields) {
            if (!reqMap.containsKey(field) || reqMap.get(field) == null) {
                ProjectUtil.errorResponse(response, "Missing required field: " + field, org.springframework.http.HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        String statusValue = String.valueOf(reqMap.get(Constants.STATUS));
        if (!Constants.APPROVED.equalsIgnoreCase(statusValue) && !Constants.REJECT.equalsIgnoreCase(statusValue)) {
            ProjectUtil.errorResponse(response, "Invalid status value. Allowed values are 'Approved' or 'Reject'", org.springframework.http.HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }
}
