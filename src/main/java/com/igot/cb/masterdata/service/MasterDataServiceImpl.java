package com.igot.cb.masterdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: mahesh.vakkund
 */
@Service
public class MasterDataServiceImpl implements MasterDataService {

    public static final Logger logger = LoggerFactory.getLogger(MasterDataServiceImpl.class);

    @Autowired
    public AccessTokenValidator accessTokenValidator;

    @Autowired
    public CassandraOperation cassandraOperation;

    @Autowired
    public CacheService redisCacheMgr;

    @Autowired
    public ProjectUtil projectUtil;

    /**
     * Retrieves a list of all institutions from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of institutions if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse getInstitutionsList(String authToken) {
        logger.info("MasterDataServiceImpl::getInstitutionsList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_GET_STATE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            Map<String, Object> institutionsMap = getInstitutionsFromCache();
            if (!MapUtils.isEmpty(institutionsMap)) {
                response.getResult().put(Constants.INSTITUTION_LIST, institutionsMap);
                logger.info("MasterDataServiceImpl::getInstitutionsList completed successfully with cached data");
                return response;
            }
            institutionsMap = getInstitutionsFromDatabase();
            if (!MapUtils.isEmpty(institutionsMap)) {
                response.getResult().put(Constants.INSTITUTION_LIST, institutionsMap);
                redisCacheMgr.putCache(Constants.INSTITUTION_LIST, institutionsMap);
                logger.info("MasterDataServiceImpl::getInstitutionsList completed successfully");
            } else {
                response.getResult().put(Constants.INSTITUTION_LIST, List.of());
                logger.info("MasterDataServiceImpl::getInstitutionsList - No institution data found");
            }
        } catch (Exception e) {
            logger.error("Error processing institutions data: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to process institutions data: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }


    /**
     * Retrieves a list of all degrees from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of degrees if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse getDegreesList(String authToken) {
        logger.info("MasterDataServiceImpl::getDegreesList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_GET_DEGREE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            Map<String, Object> degreesMap = getDegreesFromCache();
            if (!MapUtils.isEmpty(degreesMap)) {
                response.getResult().put(Constants.DEGREES_LIST, degreesMap);
                logger.info("MasterDataServiceImpl::getDegreesList completed successfully with cached data");
                return response;
            }
            degreesMap = getDegreesFromDatabase();
            if (!MapUtils.isEmpty(degreesMap)) {
                response.getResult().put(Constants.DEGREES_LIST, degreesMap);
                redisCacheMgr.putCache(Constants.DEGREES_LIST, degreesMap);
                logger.info("MasterDataServiceImpl::getDegreesList completed successfully");
            } else {
                response.getResult().put(Constants.DEGREES_LIST, List.of());
                logger.info("MasterDataServiceImpl::getDegreesList - No degrees data found");
            }
        } catch (Exception e) {
            logger.error("Error processing degrees data: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to process degrees data: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Retrieves institutions from the cache.
     *
     * @return Map of institutions if found, empty map otherwise
     */
    public Map<String, Object> getInstitutionsFromCache() {
        try {
            String institutionsJson = redisCacheMgr.getCache(Constants.INSTITUTION_LIST);
            if (!StringUtils.isEmpty(institutionsJson)) {
                return new ObjectMapper().readValue(institutionsJson, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            logger.error("Error retrieving institutions from cache: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Retrieves institutions from the database.
     *
     * @return Map of institutions if found, empty map otherwise
     */
    public Map<String, Object> getInstitutionsFromDatabase() {
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put(Constants.ID, Constants.INSTITUTIONS_CONFIG);
            List<String> fields = new ArrayList<>();
            fields.add(Constants.FIELD_KEY);
            List<Map<String, Object>> rawData = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, properties, fields, Constants.ID);
            if (rawData != null && !rawData.isEmpty()) {
                String jsonString = (String) rawData.get(0).get(Constants.FIELD_KEY);
                if (!StringUtils.isEmpty(jsonString)) {
                    return new ObjectMapper().readValue(jsonString, new TypeReference<Map<String, Object>>() {});
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving institutions from database: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Retrieves degrees from the cache.
     *
     * @return Map of degrees if found, empty map otherwise
     */
    public Map<String, Object> getDegreesFromCache() {
        try {
            String degreesJson = redisCacheMgr.getCache(Constants.DEGREES_LIST);
            if (!StringUtils.isEmpty(degreesJson)) {
                return new ObjectMapper().readValue(degreesJson, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            logger.error("Error retrieving degrees from cache: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Retrieves degrees from the database.
     *
     * @return Map of degrees if found, empty map otherwise
     */
    public Map<String, Object> getDegreesFromDatabase() {
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put(Constants.ID, Constants.DEGREES_CONFIG);
            List<String> fields = new ArrayList<>();
            fields.add(Constants.FIELD_KEY);
            List<Map<String, Object>> rawData = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, properties, fields, Constants.ID);
            if (rawData != null && !rawData.isEmpty()) {
                String jsonString = (String) rawData.get(0).get(Constants.FIELD_KEY);
                if (!StringUtils.isEmpty(jsonString)) {
                    return new ObjectMapper().readValue(jsonString, new TypeReference<Map<String, Object>>() {});
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving degrees from database: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Updates the error details in the ApiResponse object.
     *
     * @param response     The ApiResponse object to update
     * @param errorMessage The error message to set
     * @param status       The HTTP status code to set
     */
    public void updateErrorDetails(ApiResponse response, String errorMessage, HttpStatus status) {
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(Constants.FAILED);
        response.setResponseCode(status);
    }
}