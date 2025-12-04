package com.igot.cb.profile.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.igot.common.cassandra.CassandraOperation;
import org.springframework.http.HttpStatus;
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

    public List<Map<String, Object>> readUserExtendedProfile(String userId, String contextType) {
        String redisKey = projectUtil.buildCacheKey(Constants.USER_EXTENDED_PROFILE_PREFIX, contextType, userId);
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
            contextData = getExistingContextData(userId, contextType);
            if (contextData == null || contextData.isEmpty()) {
                log.error("Failed to read user extended profile for userId: {}, contextType: {}", userId, contextType);
                return contextData;
            }
            try {
                cacheService.putCache(redisKey, contextData);
            } catch (Exception e) {
                log.warn("Failed to cache data for key {}: {}", redisKey, e.getMessage());
            }
        }
        return contextData;
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
}
