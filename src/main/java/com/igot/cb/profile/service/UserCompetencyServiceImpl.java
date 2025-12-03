package com.igot.cb.profile.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserCompetencyServiceImpl implements UserCompetencyService {

    private final AccessTokenValidator accessTokenValidator;
    private final CassandraOperation cassandraOperation;
    private final CacheService cacheService;
    private final ProjectUtil projectUtil;

    public UserCompetencyServiceImpl(AccessTokenValidator accessTokenValidator, CassandraOperation cassandraOperation,
            CacheService cacheService, ProjectUtil projectUtil) {
        this.accessTokenValidator = accessTokenValidator;
        this.cassandraOperation = cassandraOperation;
        this.cacheService = cacheService;
        this.projectUtil = projectUtil;
    }

    public ApiResponse listCompetencies(String userId, String userToken) {
        ApiResponse response = ApiResponse.createDefaultResponse("api.listCompetencies.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken, response);

        if (StringUtils.isBlank(userIdFromToken)) {
            return response;
        }

        String cacheKey = Constants.USER + ":competencies:" + userId;
        try {
            String cachedJson = cacheService.getCache(cacheKey);
            Map<String, Object> competencies = (cachedJson != null) ? projectUtil.parseMap(cachedJson) : Map.of();

            if (competencies.isEmpty()) {
                Map<String, Object> queryParams = Map.of(Constants.USERID_KEY, userId);
                List<String> fields = Arrays.asList(Constants.USERID_KEY, Constants.COURSE_ID, Constants.BATCH_ID,
                        Constants.ACTIVE, Constants.STATUS);
                List<Map<String, Object>> allEnrolmentRecords = cassandraOperation.getAllRecordsByProperties(
                        Constants.KEYSPACE_SUNBIRD_COURSES,
                        Constants.TABLE_USER_ENROLMENTS, queryParams, fields, 100);
                List<String> completedCourseIdList = allEnrolmentRecords.stream()
                        .filter(map -> Boolean.TRUE.equals(map.get(Constants.ACTIVE_LOWERCASE))
                                && Integer.valueOf(2).equals(map.get(Constants.STATUS)))
                        .map(map -> map.get(Constants.COURSE_ID))
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toList());
                if (completedCourseIdList.isEmpty()) {
                    ProjectUtil.errorResponse(response, "No competencies found for user.", HttpStatus.NO_CONTENT);
                    return response;
                }
                Map<String, Map<String, Object>> courseMetadata = getCourseMetadataBatched(completedCourseIdList, 100,
                        Arrays.asList(Constants.COURSE_ID, Constants.COURSE_CATEGORY, Constants.COMPETENCIES_V6,
                                Constants.NAME));
                competencies = analyzeCompetencies(courseMetadata);

                if (competencies.isEmpty()) {
                    ProjectUtil.errorResponse(response, "No competencies found for user.", HttpStatus.NO_CONTENT);
                    return response;
                }
                cacheService.putCache(cacheKey, competencies);
            }

            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESPONSE, competencies);
        } catch (Exception e) {
            log.error("Error fetching competencies for userId: {}", userId, e);
            ProjectUtil.errorResponse(response, "Internal server error while fetching competencies",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    public Map<String, Map<String, Object>> getCourseMetadataBatched(List<String> courseIds, int batchSize,
            List<String> fields) {
        Map<String, Map<String, Object>> allResults = new LinkedHashMap<>();
        if (courseIds == null || courseIds.isEmpty())
            return allResults;

        for (int i = 0; i < courseIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, courseIds.size());
            List<String> batch = courseIds.subList(i, end);

            Map<String, String> courseDetailsStrMap = cacheService.getCourseMetadataAsJsonString(batch);

            for (int j = 0; j < batch.size(); j++) {
                String courseId = batch.get(j);
                String json = courseDetailsStrMap.get(courseId);

                if (json != null) {
                    try {
                        Map<String, Object> parsed = projectUtil.parseMap(json);
                        if (parsed == null || parsed.isEmpty()) {
                            log.warn("Parsed JSON for key {} is empty or null", courseId);
                            continue;
                        }

                        if (fields == null || fields.isEmpty()) {
                            allResults.put(courseId, parsed);
                        } else {
                            // Filter only requested fields
                            Map<String, Object> filtered = parsed.entrySet().stream()
                                    .filter(e -> fields.contains(e.getKey()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                            if (!filtered.isEmpty()) {
                                allResults.put(courseId, filtered);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse JSON for key {}: {}", courseId, e.getMessage(), e);
                    }
                } else {
                    log.warn("No cached data found for courseId: {}", courseId);
                }
            }
        }
        return allResults;
    }

    public Map<String, Object> analyzeCompetencies(Map<String, Map<String, Object>> courseMetadata) {
        Map<String, Object> emptyResult = new HashMap<>();
        emptyResult.put(Constants.COMPETENCY_AREA_COUNTS, new HashMap<>());
        emptyResult.put(Constants.COMPETENCY_THEME_GROUPS, new HashMap<>());

        try {
            Map<String, Long> areaCountMap = new HashMap<>();
            Map<String, Map<String, Object>> themeGroupMap = new HashMap<>();

            if (courseMetadata == null || courseMetadata.isEmpty()) {
                return emptyResult;
            }

            for (Map.Entry<String, Map<String, Object>> entry : courseMetadata.entrySet()) {
                String courseId = entry.getKey();
                Map<String, Object> course = entry.getValue();
                if (course == null) {
                    continue;
                }

                Object compObj = course.get(Constants.COMPETENCIES_V6);
                if (!(compObj instanceof List<?> competencies))
                    continue;

                for (Object comp : competencies) {
                    if (!(comp instanceof Map<?, ?> compMap))
                        continue;

                    String areaName = String.valueOf(compMap.get(Constants.COMPETENCY_AREA_NAME));
                    String themeName = String.valueOf(compMap.get(Constants.COMPETENCY_THEME_NAME));
                    String subThemeName = String.valueOf(compMap.get(Constants.COMPETENCY_SUB_THEME_NAME));

                    // 1. Count by competencyAreaName
                    areaCountMap.merge(areaName, 1L, Long::sum);

                    // 2. Group by competencyThemeName
                    Map<String, Object> themeGroup = themeGroupMap.computeIfAbsent(themeName, k -> new HashMap<>());
                    Set<String> subThemes = (Set<String>) themeGroup.computeIfAbsent(
                            Constants.COMPETENCY_SUB_THEME_NAMES, k -> new HashSet<String>());
                    Set<String> courseIds = (Set<String>) themeGroup.computeIfAbsent(
                            Constants.COURSE_IDS, k -> new HashSet<String>());

                    if (subThemeName != null && !subThemeName.isBlank())
                        subThemes.add(subThemeName);
                    courseIds.add(courseId);
                }
            }

            // Prepare final output
            Map<String, Object> result = new HashMap<>();
            result.put(Constants.COMPETENCY_AREA_COUNTS, areaCountMap);

            // Convert sets to lists for serialization/final response
            Map<String, Map<String, Object>> groupedThemes = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : themeGroupMap.entrySet()) {
                groupedThemes.put(entry.getKey(), Map.of(
                        Constants.COMPETENCY_SUB_THEME_NAMES,
                        new ArrayList<>((Set<?>) entry.getValue().get(Constants.COMPETENCY_SUB_THEME_NAMES)),
                        Constants.COURSE_IDS, new ArrayList<>((Set<?>) entry.getValue().get(Constants.COURSE_IDS))));
            }

            result.put(Constants.COMPETENCY_THEME_GROUPS, groupedThemes);
            return result;
        } catch (Exception e) {
            log.warn("analyzeCompetencies failed, returning empty result: {}", e.getMessage());
            return emptyResult;
        }
    }
}
