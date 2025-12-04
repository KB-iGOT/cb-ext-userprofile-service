package com.igot.cb.profile.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.igot.common.cassandra.CassandraOperation;
import org.igot.common.service.OutboundRequestHandlerServiceImpl;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProfilePreference;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserInfoHelperServiceImpl {

    private CbServerProperties serverConfig;
    private final CacheService cacheService;
    private final CassandraOperation cassandraOperation;
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;
    private final ObjectMapper mapper;
    private final ProfileReaderServiceImpl profileReaderService;

    public UserInfoHelperServiceImpl(CbServerProperties serverConfig, CacheService cacheService,
            CassandraOperation cassandraOperation, OutboundRequestHandlerServiceImpl outboundRequestHandlerService, ObjectMapper mapper, ProfileReaderServiceImpl profileReaderService) {
        this.serverConfig = serverConfig;
        this.cacheService = cacheService;
        this.cassandraOperation = cassandraOperation;
        this.outboundRequestHandlerService = outboundRequestHandlerService;
        this.mapper = mapper;
        this.profileReaderService = profileReaderService;
    }    

    public int getUserKarmaPoints(String userId) {
        String redisKey = "user:karmaPoints:" + userId;

        try {
            String redisValue = cacheService.getCache(redisKey);
            if (redisValue != null) {
                return Integer.parseInt(redisValue);
            }

            List<Map<String, Object>> records = cassandraOperation.getRecordsByProperties(Constants.KEYSPACE_SUNBIRD, Constants.USER_KARMA_POINTS_SUMMARY_TABLE,
                    Map.of(Constants.USERID_KEY, userId), List.of(Constants.TOTAL_POINTS), null);
            int totalPoints = 0;
            if (!CollectionUtils.isEmpty(records)) {
                totalPoints = (int) records.get(0).get(Constants.TOTAL_POINTS);
            }

            cacheService.putCache(redisKey, totalPoints);
            return totalPoints;
        } catch (Exception e) {
            log.warn("Failed to fetch karma points for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    public int getIssuedCertificateCount(String userId) {
        String redisKey = serverConfig.getCertificateCountRedisKey();

        try {
            String cachedValue = cacheService.hget(redisKey,serverConfig.getDataIndex(),userId,serverConfig.getCacheTtl());
            if (cachedValue != null) {
                return Integer.parseInt(cachedValue);
            }
            List<Map<String, Object>> courseRecords = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD_COURSES,
                    serverConfig.getUserEnrolmentsTable(),
                    Map.of(Constants.USERID_KEY, userId),
                    List.of(Constants.ISSUED_CERTIFICATES),
                    null
            );

            int totalIssuedCertificates = 0;
            totalIssuedCertificates += (int) courseRecords.stream()
                    .filter(MapUtils::isNotEmpty)
                    .map(record -> record.get(Constants.ISSUED_CERTIFICATES_KEY))
                    .filter(certObj -> certObj instanceof List<?>)
                    .map(certObj -> (List<?>) certObj)
                    .filter(CollectionUtils::isNotEmpty)
                    .count();

            List<Map<String, Object>> eventRecords = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD_COURSES,
                    Constants.USER_ENTITY_ENROLMENTS,
                    Map.of(Constants.USERID_KEY, userId),
                    List.of(Constants.ISSUED_CERTIFICATES,Constants.PROGRESS_KEY,Constants.STATUS),
                    null
            );

            int certificatesFromEvents = (int) eventRecords.stream()
                    .filter(MapUtils::isNotEmpty)
                    .filter(r -> r.get(Constants.STATUS) instanceof Number numberStatus && numberStatus.intValue() == 2)
                    .filter(r -> r.get(Constants.PROGRESS_KEY) instanceof Number numberProgress && numberProgress.intValue() == 100)
                    .map(r -> r.get(Constants.ISSUED_CERTIFICATES_KEY))
                    .filter(obj -> obj instanceof List<?>)
                    .map(obj -> (List<?>) obj)
                    .filter(CollectionUtils::isNotEmpty)
                    .count();

            List<Map<String, Object>> externalCourseRecords = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD_COURSES,
                    Constants.USER_EXTERNAL_COURSE_ENROLMENTS,
                    Map.of(Constants.USERID_KEY, userId),
                    List.of(Constants.ISSUED_CERTIFICATES,Constants.PROGRESS_KEY,Constants.STATUS),
                    null
            );

            int certificatesFromExternalCourses = (int) externalCourseRecords.stream()
                    .filter(MapUtils::isNotEmpty)
                    .filter(r -> r.get(Constants.STATUS) instanceof Number numberStatus && numberStatus.intValue() == 2)
                    .filter(r -> r.get(Constants.PROGRESS_KEY) instanceof Number numberProgress && numberProgress.intValue() == 100)
                    .map(r -> r.get(Constants.ISSUED_CERTIFICATES_KEY))
                    .filter(obj -> obj instanceof List<?>)
                    .map(obj -> (List<?>) obj)
                    .filter(CollectionUtils::isNotEmpty)
                    .count();
            totalIssuedCertificates += certificatesFromEvents + certificatesFromExternalCourses;
            cacheService.hset(redisKey,serverConfig.getDataIndex(),userId, String.valueOf(totalIssuedCertificates));
            return totalIssuedCertificates;

        } catch (Exception e) {
            log.warn("Failed to fetch issued certificate count for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    public int getUserPostCount(String userId) {
        String redisKey = "user:postCount_" + userId;

        try {
            String cachedValue = cacheService.getCache(redisKey);
            if (cachedValue != null) {
                return Integer.parseInt(cachedValue);
            }

            int postCount = fetchPostCountFromApi(userId);
            cacheService.putCache(redisKey, postCount);
            return postCount;

        } catch (Exception e) {
            log.warn("Failed to fetch post count for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private int fetchPostCountFromApi(String userId) {
        String uri = serverConfig.getCommunityBaseUrl() + serverConfig.getCommunityPostCountApiUrl() + userId;

        try {
            Map<String, Object> response = outboundRequestHandlerService.fetchUsingGetWithHeadersProfile(uri, null);

            return Optional.ofNullable(response)
                    .filter(MapUtils::isNotEmpty)
                    .map(rd -> (Map<String, Object>) rd.get(Constants.RESULT))
                    .filter(MapUtils::isNotEmpty)
                    .map(result -> result.get(Constants.POSTCOUNT))
                    .filter(Integer.class::isInstance)
                    .map(Integer.class::cast)
                    .orElse(0);

        } catch (Exception e) {
            log.warn("Failed to fetch post count from community API for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    public List<String> getUserRoles(String userId, String rootOrgId) {
        List<Map<String, Object>> userRoleList = cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_ROLES,
                Map.of(Constants.USERID_KEY, userId), List.of(Constants.ROLE, Constants.SCOPE), null
        );
        return userRoleList.stream()
                .map(userRoleObj -> {
                    Object userRoleScope = userRoleObj.get(Constants.SCOPE);
                    List<Map<String, Object>> scopes = new ArrayList<>();
                    if (userRoleScope instanceof List) {
                        scopes = (List<Map<String, Object>>) userRoleScope;
                    } else if (userRoleScope instanceof String scopeStr && !scopeStr.isBlank()) {
                        try {
                            scopes = mapper.readValue(scopeStr, new TypeReference<List<Map<String, Object>>>() {
                            });
                        } catch (Exception e) {
                            log.warn("Failed to parse scope JSON for userId {}: {}", userId, e.getMessage());
                            return null;
                        }
                    }
                    if (!scopes.isEmpty() && scopes.stream().allMatch(scope -> rootOrgId.equals(scope.get(Constants.ORGANISATION_ID)))) {
                        return (String) userRoleObj.get(Constants.ROLE);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public double calculateProfileCompletionPercentage(Map<String, Object> profileData,
                                                          String userId) {
        List<String> requiredFields = serverConfig.getProfileCompletionRequiredFields();
        if (profileData == null || requiredFields == null || requiredFields.isEmpty())
            return 0.0;

        double totalCompletion = 0.0;
        Map<String, Object> nestedData = Optional.ofNullable(profileData.get(Constants.PROFILE_DETAILS))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .orElse(Collections.emptyMap());

        for (String field : requiredFields) {
            boolean isFilled;
            try {
                if (isExtendedProfileField(field)) {
                    isFilled = hasExtendedProfileData(userId, field)
                            || (Constants.SERVICE_HISTORY.equalsIgnoreCase(field) &&
                            Optional.ofNullable(profileData.get(Constants.PROFILE_DETAILS))
                                    .filter(Map.class::isInstance)
                                    .map(Map.class::cast)
                                    .map(details -> details.get(Constants.PROFESSIONAL_DETAILS))
                                    .filter(List.class::isInstance)
                                    .map(List.class::cast)
                                    .map(CollectionUtils::isNotEmpty)
                                    .orElse(false));
                } else {
                    if (Constants.EMPLOYMENT_DETAILS.equalsIgnoreCase(field)) {
                        isFilled = Optional.ofNullable(profileData.get(Constants.PROFILE_DETAILS))
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .map(details -> details.get(Constants.EMPLOYMENT_DETAILS))
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .map(empDetails -> empDetails.get(Constants.ABOUT_ME))
                                .map(Object::toString)
                                .filter(aboutMe -> !aboutMe.trim().isEmpty())
                                .isPresent();
                    }else {
                        Object value = profileData.getOrDefault(field, nestedData.get(field));
                        isFilled = value != null && !value.toString().trim().isEmpty();
                    }
                }
            } catch (Exception e) {
                log.warn("Exception checking field '{}' for user '{}': {}", field, userId, e.getMessage());
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

    private boolean hasExtendedProfileData(String userId, String contextType) {
        try {
            List<Map<String, Object>> contextData = profileReaderService.getExistingContextData(userId, contextType);
            return !CollectionUtils.isEmpty(contextData);
        } catch (Exception e) {
            log.error("Error checking extended profile data for userId {} and contextType {}: {}", userId,
                    contextType, e.getMessage());
        }
        return false;
    }

    public void sanitizeProfile(Map<String, Object> profile, String userToken) {
        Object detailsObj = profile.get(Constants.PROFILE_DETAILS);

        if (detailsObj instanceof Map<?, ?> detailsMap) {
            if (detailsMap.containsKey(Constants.PERSONAL_DETAILS)) {
                detailsMap.remove(Constants.PERSONAL_DETAILS);
                log.info("Removed personalDetails due to unrecognized profilePreference.");
            }
            ProfilePreference profilePref = ProfilePreference.PUBLIC; // default to PUBLIC

            Object preferenceObj = detailsMap.get(Constants.PROFILE_PREFERENCE);
            if (preferenceObj instanceof Integer prefInteger) {
                ProfilePreference resolvedPref = ProfilePreference.fromValue(prefInteger);
                if (resolvedPref != null) {
                    profilePref = resolvedPref;
                }
            }

            // If PUBLIC, return everything
            if (ProfilePreference.PUBLIC.equals(profilePref)) {
                return;
            }

            // Load keys from property
            List<String> filteredKeys = Arrays.asList(serverConfig.getBasicDetailsFilteredKeys().split(","));
            // Shared allowed keys from config
            List<String> allowedKeys = Arrays.asList(serverConfig.getProfileVisibleAllowedFields().split(","));
            Map<String, Object> filteredDetails = new HashMap<>();

            // If PRIVATE_NO_ONE
            if (ProfilePreference.PRIVATE_NO_ONE.equals(profilePref)) {
                for (String key : allowedKeys) {
                    if (detailsMap.containsKey(key)) {
                        filteredDetails.put(key, detailsMap.get(key));
                    }
                }
                filteredKeys.forEach(profile::remove);
                profile.put(Constants.PROFILE_DETAILS, filteredDetails);
                log.info("Sanitized profileDetails for PRIVATE_NO_ONE ({}). Allowed fields: {}", profilePref.getValue(), allowedKeys);

            } else if (ProfilePreference.PRIVATE_CONNECTIONS.equals(profilePref)) {
                Map<String, Object> connectionResponse = checkConnected(
                        (String) profile.get(Constants.ID),
                        (String) profile.get(Constants.AUTH_TOKEN),
                        userToken);

                if (connectionResponse != null) {
                    Object statusObj = connectionResponse.get(Constants.STATUS);
                    if (statusObj != null && Constants.APPROVED.equalsIgnoreCase(statusObj.toString())) {
                        return; // If connection approved, allow full profile
                    }
                }
                filteredKeys.forEach(profile::remove);
                for (String key : allowedKeys) {
                    if (detailsMap.containsKey(key)) {
                        filteredDetails.put(key, detailsMap.get(key));
                    }
                }
                profile.put(Constants.PROFILE_DETAILS, filteredDetails);
                log.info("Sanitized profileDetails for PRIVATE_CONNECTIONS ({}). Allowed fields: {}", profilePref.getValue(), allowedKeys);

            } else {
                // Fallback case – remove personalDetails
                if (detailsMap.containsKey(Constants.PERSONAL_DETAILS)) {
                    detailsMap.remove(Constants.PERSONAL_DETAILS);
                    log.info("Removed personalDetails due to unrecognized profilePreference.");
                }
            }
        }
    }

    public Map<String, Object> checkConnected(String userId, String authToken, String userAuthToken) {
        Map<String, String> header = new HashMap<>();
        if (StringUtils.isNotEmpty(authToken)) {
            header.put(Constants.AUTH_TOKEN, authToken);
        }
        if (StringUtils.isNotEmpty(userAuthToken)) {
            header.put(Constants.X_AUTH_TOKEN, userAuthToken);
        }
        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> readData = outboundRequestHandlerService
                .fetchUsingGetWithHeadersProfile(serverConfig.hubGraphService + serverConfig.connectionApi + userId,
                        header);
        if (readData != null) {
            Object resultObj = readData.get(Constants.RESULT);
            if (resultObj instanceof Map<?, ?> resultMap) {
                Object responseObj = resultMap.get(Constants.RESPONSE);
                if (responseObj instanceof Map<?, ?> responseData) {
                    for (Map.Entry<?, ?> entry : responseData.entrySet()) {
                        if (entry.getKey() instanceof String) {
                            responseMap.put((String) entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        }

        return responseMap;
    }
}
