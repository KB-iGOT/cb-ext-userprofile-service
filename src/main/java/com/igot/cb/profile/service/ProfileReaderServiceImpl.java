package com.igot.cb.profile.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.igot.common.cassandra.CassandraOperation;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProfileReaderServiceImpl {
    private final CassandraOperation cassandraOperation;
    private final CbServerProperties serverConfig;
    private final CacheService cacheService;
    private final ProjectUtil projectUtil;
    private final ObjectMapper mapper;

    public ProfileReaderServiceImpl(CassandraOperation cassandraOperation, CbServerProperties serverConfig,
            CacheService cacheService, ProjectUtil projectUtil, ObjectMapper mapper) {
        this.cassandraOperation = cassandraOperation;
        this.serverConfig = serverConfig;
        this.cacheService = cacheService;
        this.projectUtil = projectUtil;
        this.mapper = mapper;
    }

    public Map<String, Object> readUserDataFromDB(String userId, List<String> keyList) {
        if (CollectionUtils.isEmpty(keyList)) {
            keyList = serverConfig.getBasicProfileFields();
        }
        String cacheKey = Constants.USER + ":basicProfile:" + userId;
        Map<String, Object> queryParams = Map.of(Constants.ID, userId);
        List<Map<String, Object>> userList = cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD, Constants.USER, queryParams, keyList, null);

        if (CollectionUtils.isEmpty(userList)) {
            return Map.of();
        }
        Map<String, Object> userObj = userList.get(0);
        String profileDetailsJson = (String) userObj.get(Constants.PROFILE_DETAILS);

        try {
            if (StringUtils.isNotBlank(profileDetailsJson)) {
                Map<String, Object> profileDetailsMap = mapper.readValue(profileDetailsJson,
                        new TypeReference<Map<String, Object>>() {
                        });
                userObj.put(Constants.PROFILE_DETAILS, profileDetailsMap);
            } else {
                userObj.put(Constants.PROFILE_DETAILS, Map.of());
            }
            cacheService.putCache(cacheKey, userObj);
        } catch (IOException e) {
            log.error("Invalid profileDetails JSON for userId: {}", userId, e);
            userObj.put(Constants.PROFILE_DETAILS, Map.of());
        }

        return userObj;
    }

    public Map<String, Object> readUserExtendedProfile(String userId) {
        String redisKey = projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, "all", userId);
        
        try {
            String cachedJson = cacheService.getCache(redisKey);
            if (cachedJson != null) {
                Map<String, Object> cachedResult = projectUtil.parseMap(cachedJson);
                return buildLimitedSummary(cachedResult);
            }
        } catch (Exception e) {
            log.warn("Error reading from cache for key {}: {}", redisKey, e.getMessage());
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

        try {
            cacheService.putCache(redisKey, result);
        } catch (Exception e) {
            log.warn("Failed to cache extended profile summary for userId {}: {}", userId, e.getMessage());
        }
        return result;
    }

    public List<Map<String, Object>> getExistingContextData(String userId, String contextType) {
        Map<String, Object> query = Map.of(Constants.USERID_KEY, userId, Constants.CONTEXT_TYPE, contextType);
        List<Map<String, Object>> rows = cassandraOperation.getRecordsByProperties(Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);
        if (rows != null && !rows.isEmpty()) {
            String json = (String) rows.get(0).get(Constants.CONTEXT_DATA);
            try {
                return projectUtil.parseListOfMap(json);
            } catch (IOException e) {
                log.error("Error parsing existing data for userId: {}, contextType: {}", userId, contextType);
            }
        }
        return new ArrayList<>();
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
}
