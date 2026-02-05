package com.igot.cb.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AchievementServiceImpl implements AchievementService{

    private List<String> requiredFields;

    private final AccessTokenValidator accessTokenValidator;

    private final CbServerProperties cbServerProperties;

    private final CassandraOperation cassandraOperation;

    private final EsClientService esClientService;

    private final ObjectMapper objectMapper;

    private final RedisTemplate<String, SearchResult> redisTemplate;

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
            ObjectMapper objectMapper,
            @Qualifier(Constants.SEARCH_RESULT_REDIS_TEMPLATE) RedisTemplate<String, SearchResult> redisTemplate,
            CacheService cacheService) {
        this.accessTokenValidator = accessTokenValidator;
        this.cbServerProperties = cbServerProperties;
        this.cassandraOperation = cassandraOperation;
        this.esClientService = esClientService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.cacheService = cacheService;
    }

    @PostConstruct
    private void initRequiredFields() {
        requiredFields = Arrays.asList(cbServerProperties.getRequiredFieldsProperty().split(","));
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
            compositeKey.put(Constants.USER_ID_LOWER, reqMap.get(FIELD_LEARNER_ID));
            compositeKey.put(Constants.FIELD_CONTEXT_TYPE, reqMap.get(Constants.CONTEXT_TYPE_KEY));
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

    @Override
    public ApiResponse searchLearnerAchievements(SearchCriteria searchCriteria, String authToken) {
        log.info("AchievementService::searchLearnerAchievements");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_SEARCH);
        String cacheKey = generateRedisJwtTokenKey(searchCriteria);
        SearchResult searchResult = redisTemplate.opsForValue().get(cacheKey);
        if (searchResult != null) {
            log.info("DiscussionServiceImpl::searchDiscussion:  search result fetched from redis");
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            return response;
        }
        String searchString = searchCriteria.getSearchString();
        if (searchString != null && !searchString.isEmpty() && searchString.length() < 3) {
            ProjectUtil.errorResponse(response, Constants.MINIMUM_CHARACTERS_NEEDED, HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            log.info("DiscussionServiceImpl::searchDiscussion:  search result fetched from es");
            if (MapUtils.isEmpty(searchCriteria.getFilterCriteriaMap())) {
                searchCriteria.setFilterCriteriaMap(new HashMap<>());
            }
            searchResult = esClientService.searchDocuments(Constants.LEARNER_ACHIEVEMENT_INDEX, searchCriteria);
            if (CollectionUtils.isEmpty(searchResult.getData())) {
                ProjectUtil.errorResponse(response, Constants.NO_DATA_FOUND, HttpStatus.OK);
                response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
                return response;
            }
            List<Map<String, Object>> achievemnets = searchResult.getData();
            searchResult.setUserDetails(fetchUsernamesFromSearchData(achievemnets));
            searchResult.setData(achievemnets);
            redisTemplate.opsForValue().set(cacheKey, searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            return response;
        } catch (Exception e) {
            log.error("error while searching discussion : {} .", e.getMessage(), e);
            ProjectUtil.errorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
    }

    /**
     * Extracts unique userIds from search result data and fetches usernames for each.
     * @param data List of search result maps (each representing a record)
     * @return Map of userId to username
     */
    private Map<String, String> fetchUsernamesFromSearchData(List<Map<String, Object>> data) {
        Set<String> uniqueUserIds = new HashSet<>();
        for (Map<String, Object> item : data) {
            Object userIdObj = item.get(Constants.USER_ID);
            if (StringUtils.isEmpty((String) userIdObj)) userIdObj = item.get(Constants.USER_ID_LOWER);
            if (userIdObj instanceof String && StringUtils.isNotBlank((String) userIdObj)) {
                uniqueUserIds.add((String) userIdObj);
            }
        }
        // Fetch user details (replace with actual Redis/Cassandra logic)
        List<Object> userDetailsList = fetchUserDetails(new ArrayList<>(uniqueUserIds));
        Map<String, String> userIdToUsername = new HashMap<>();
        for (Object user : userDetailsList) {
            if (user instanceof Map) {
                Map userMap = (Map) user;
                Object idObj = userMap.get(Constants.USER_ID_KEY);
                Object nameObj = userMap.get(Constants.FIRST_NAME_KEY);
                if (idObj instanceof String && nameObj instanceof String) {
                    userIdToUsername.put((String) idObj, (String) nameObj);
                }
            }
        }
        return userIdToUsername;
    }

    private List<Object> fetchUserDetails(List<String> userIds) {
        // Prepare Redis keys (assuming prefix is needed)
        List<String> redisKeys = userIds.stream()
            .map(id -> Constants.USER_PREFIX + id)
            .collect(Collectors.toList());
        // Fetch values for all keys from Redis
        List<Object> redisResults = cacheService.hget(redisKeys); // Use your cacheService
        // Build userDetailsMap from redis results
        Map<String, Object> userDetailsMap = redisResults.stream()
                .filter(Objects::nonNull)
                .map(user -> (Map<String, Object>) user)
                .filter(user -> user.get(Constants.USER_ID_KEY) != null)
                .collect(Collectors.toMap(
                        user -> user.get(Constants.USER_ID_KEY).toString(),
                        user -> user,
                        (u1, u2) -> u1));
        // Find missing userIds
        List<String> missingUserIds = userIds.stream()
                .filter(id -> !userDetailsMap.containsKey(id))
                .collect(Collectors.toList());
        // Fetch from Cassandra if missing
        if (!missingUserIds.isEmpty()) {
            List<Object> cassandraResults = fetchUserFromPrimary(missingUserIds);
            userDetailsMap.putAll(cassandraResults.stream()
                    .map(user -> (Map<String, Object>) user)
                    .filter(user -> user.get(Constants.USER_ID_KEY) != null)
                    .collect(Collectors.toMap(
                            user -> user.get(Constants.USER_ID_KEY).toString(),
                            user -> user,
                            (u1, u2) -> u1)));
        }
        return new ArrayList<>(userDetailsMap.values());
    }

    public List<Object> fetchUserFromPrimary(List<String> userIds) {
        log.info("AchievementServiceImpl::fetchUserFromPrimary: Fetching user data from Cassandra");
        List<Object> userList = new ArrayList<>();
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userIds);
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_TABLE, propertyMap,
                Arrays.asList(Constants.FIRST_NAME, Constants.ID), null);
        // updateMetricsDbOperation(Constants.DISCUSSION_SEARCH, Constants.CASSANDRA, Constants.READ, startTime);
        userList = userInfoList.stream()
                .map(userInfo -> {
                    Map<String, Object> userMap = new HashMap<>();
                    String userId = (String) userInfo.get(Constants.ID);
                    String userName = (String) userInfo.get(Constants.FIRST_NAME_CAMEL_CASE);
                    userMap.put(Constants.USER_ID_KEY, userId);
                    userMap.put(Constants.FIRST_NAME_KEY, userName);
                    return userMap;
                })
                .collect(Collectors.toList());
        return userList;
    }

    public String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
                return JWT.create()
                        .withClaim(Constants.REQUEST, reqJsonString)
                        .sign(Algorithm.HMAC256(Constants.JWT_SECRET_KEY));
            } catch (JsonProcessingException e) {
                log.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }
}
