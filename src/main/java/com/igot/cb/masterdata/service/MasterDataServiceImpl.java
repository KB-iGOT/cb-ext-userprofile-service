package com.igot.cb.masterdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.masterdata.model.Degree;
import com.igot.cb.masterdata.model.Institute;
import com.igot.cb.masterdata.model.SearchCriteria;
import com.igot.cb.masterdata.repository.DegreeRepository;
import com.igot.cb.masterdata.repository.InstituteRepository;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.model.EsResponse;
import com.igot.cb.transactional.elasticsearch.service.EsUtilService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Author: mahesh.vakkund
 */
@Service
@SuppressWarnings("unchecked")
public class MasterDataServiceImpl implements MasterDataService {

    public static final Logger logger = LoggerFactory.getLogger(MasterDataServiceImpl.class);

    @Autowired
    public AccessTokenValidator accessTokenValidator;

    @Autowired
    public CassandraOperation cassandraOperation;

    @Autowired
    public CacheService redisCacheMgr;

    @Autowired
    private DegreeRepository degreeRepository;

    @Autowired
    private InstituteRepository instituteRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private EsUtilService esUtilService;

    @Autowired
    private CbServerProperties serverProperties;

    @Autowired
    @Qualifier("igotESClient")
    private RestHighLevelClient igotESClient;

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

    /**
     * Updates the institution list in the master data.
     *
     * @param authToken    The authentication token for authorizing the request
     * @param requestBody  The request body containing the institution name to add
     * @return ApiResponse containing the updated institution list if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse updateInstitutionList(String authToken, Map<String, Object> requestBody) {
        logger.info("MasterDataServiceImpl::updateInstitutionList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_INSTITUTION_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        if (!validateInstitutionRequest(requestBody, response)) {
            return response;
        }
        try {
            processInstitutionUpdate(requestBody, response);
        } catch (Exception e) {
            logger.error("Error updating institution: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to update institution: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }



    /**
     * Validates the institution request body.
     *
     * @param requestBody The request body to validate
     * @param response    The ApiResponse object to update with error details if validation fails
     * @return true if the request body is valid, false otherwise
     */
    private boolean validateInstitutionRequest(Map<String, Object> requestBody, ApiResponse response) {
        if (MapUtils.isEmpty(requestBody) || !requestBody.containsKey(Constants.INSTITUTE_NAME) ||
                requestBody.get(Constants.INSTITUTE_NAME) == null) {
            updateErrorDetails(response, "Institution name is required", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    /**
     * Processes the institution update request.
     *
     * @param requestBody The request body containing the institution name to add
     * @param response    The ApiResponse object to update with the result
     */
    protected void processInstitutionUpdate(Map<String, Object> requestBody, ApiResponse response) {
        String institutionName = (String) requestBody.get(Constants.INSTITUTE_NAME);
        Map<String, Object> institutionsMap = retrieveInstitutionsData(response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        List<String> institutionList = getInstitutionList(institutionsMap, response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        if (updateOrAddInstitution(institutionList, institutionName)) {
            try {
                saveInstitutionChangesToDatabaseAndCache(institutionsMap);
                response.getResult().put(Constants.RESPONSE, "Institution added successfully : " + institutionName);
                response.setResponseCode(HttpStatus.CREATED);
                logger.info("MasterDataServiceImpl::updateInstitutionList completed successfully");
            } catch (Exception e) {
                updateErrorDetails(response, "Failed to update institution: " + e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
                logger.error("Error saving institution changes: {}", e.getMessage(), e);
            }
        } else {
            response.getResult().put(Constants.RESPONSE, "Institution already exists");
            response.setResponseCode(HttpStatus.OK);
        }
    }

    /**
     * Retrieves institutions data from cache or database.
     *
     * @param response The ApiResponse object to update with error details if retrieval fails
     * @return Map containing institutions data, or empty map if error occurred
     */
    private Map<String, Object> retrieveInstitutionsData(ApiResponse response) {
        Map<String, Object> institutionsMap = getInstitutionsFromCache();
        if (MapUtils.isEmpty(institutionsMap)) {
            institutionsMap = getInstitutionsFromDatabase();
            if (MapUtils.isEmpty(institutionsMap)) {
                updateErrorDetails(response, "No institutions data found", HttpStatus.NOT_FOUND);
            }
        }
        return institutionsMap;
    }

    /**
     * Extracts institution list from institutions map and validates it.
     *
     * @param institutionsMap Map containing institutions data
     * @param response The ApiResponse object to update with error details if validation fails
     * @return List of institutions, or null if error occurred
     */
    private List<String> getInstitutionList(Map<String, Object> institutionsMap, ApiResponse response) {
        List<String> institutionList = (List<String>) institutionsMap.get(Constants.INSTITUTIONS);
        if (CollectionUtils.isEmpty(institutionList)) {
            logger.error("Invalid institutions data: institutions list is null or not a valid list");
            updateErrorDetails(response, "Invalid institutions data format", HttpStatus.INTERNAL_SERVER_ERROR);
            return Collections.emptyList();
        }
        return institutionList;
    }

    /**
     * Updates or adds an institution to the list of institutions.
     *
     * @param institutions    The list of institutions to update
     * @param institutionName The institution name to add
     * @return true if the institution was added, false if it already existed
     */
    private boolean updateOrAddInstitution(List<String> institutions, String institutionName) {
        boolean exists = institutions.stream()
                .anyMatch(institution -> institution.equals(institutionName));
        if (!exists) {
            institutions.add(institutionName);
            Collections.sort(institutions);
            return true;
        }
        return false;
    }


    /**
     * Saves the updated institution list to the database and cache.
     *
     * @param institutionsMap The map containing the updated institution list
     * @throws JsonProcessingException If there is an error during JSON processing
     */
    public void saveInstitutionChangesToDatabaseAndCache(Map<String, Object> institutionsMap)
            throws JsonProcessingException {
        Map<String, Object> updateMap = new HashMap<>();
        String jsonString = new ObjectMapper().writeValueAsString(institutionsMap);
        updateMap.put(Constants.FIELD_KEY, jsonString);
        updateMap.put(Constants.ID, Constants.INSTITUTIONS_CONFIG);
        cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, updateMap);
        redisCacheMgr.putCache(Constants.INSTITUTION_LIST, institutionsMap);
    }

    /**
     * Retrieves the list of degrees from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of degrees if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse updateDegreesList(String authToken, Map<String, Object> requestBody) {
        logger.info("MasterDataServiceImpl::updateDegreesList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_DEGREE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        if (!validateDegreeRequest(requestBody, response)) {
            return response;
        }
        try {
            processDegreeUpdate(requestBody, response);
        } catch (Exception e) {
            logger.error("Error updating degree: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to update degree: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Validates the degree request body.
     *
     * @param requestBody The request body to validate
     * @param response    The ApiResponse object to update with error details if validation fails
     * @return true if the request body is valid, false otherwise
     */
    protected boolean validateDegreeRequest(Map<String, Object> requestBody, ApiResponse response) {
        if (MapUtils.isEmpty(requestBody) ||
                !requestBody.containsKey(Constants.DEGREE_NAME)) {
            updateErrorDetails(response, "Degree name is required", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    /**
     * Processes the degree update request.
     *
     * @param requestBody The request body containing the degree name to add
     * @param response    The ApiResponse object to update with the result
     */
    protected void processDegreeUpdate(Map<String, Object> requestBody, ApiResponse response) {
        String degreeName = (String) requestBody.get(Constants.DEGREE_NAME);
        Map<String, Object> degreesMap = retrieveDegreesData(response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        List<String> degreesList = getDegreesList(degreesMap, response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        if (updateOrAddDegree(degreesList, degreeName)) {
            try {
                saveDegreeChangesToDatabaseAndCache(degreesMap);
                response.getResult().put(Constants.RESPONSE, "Degree added successfully : " + degreeName);
                response.setResponseCode(HttpStatus.CREATED);
                logger.info("MasterDataServiceImpl::updateDegreesList completed successfully");
            } catch (Exception e) {
                updateErrorDetails(response, "Failed to update degree: " + e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
                logger.error("Error saving degree changes: {}", e.getMessage(), e);
            }
        } else {
            response.getResult().put(Constants.RESPONSE, "Degree already exists");
            response.setResponseCode(HttpStatus.OK);
        }
    }

    /**
     * Retrieves degrees data from cache or database.
     *
     * @param response The ApiResponse object to update with error details if retrieval fails
     * @return Map containing degrees data, or empty map if error occurred
     */
    protected Map<String, Object> retrieveDegreesData(ApiResponse response) {
        Map<String, Object> degreesMap = getDegreesFromCache();
        if (MapUtils.isEmpty(degreesMap)) {
            degreesMap = getDegreesFromDatabase();
            if (MapUtils.isEmpty(degreesMap)) {
                updateErrorDetails(response, "No degrees data found", HttpStatus.NOT_FOUND);
            }
        }
        return degreesMap;
    }

    /**
     * Extracts degree list from degrees map and validates it.
     *
     * @param degreesMap Map containing degrees data
     * @param response The ApiResponse object to update with error details if validation fails
     * @return List of degrees, or null if error occurred
     */
    protected List<String> getDegreesList(Map<String, Object> degreesMap, ApiResponse response) {
        List<String> degreesList = (List<String>) degreesMap.get(Constants.DEGREES);
        if (CollectionUtils.isEmpty(degreesList)) {
            logger.error("Invalid degrees data: degrees list is null or not a valid list");
            updateErrorDetails(response, "Invalid degrees data format", HttpStatus.INTERNAL_SERVER_ERROR);
            return Collections.emptyList();
        }
        return degreesList;
    }

    /**
     * Saves the updated degree list to the database and cache.
     *
     * @param degreesMap The map containing the updated degree list
     * @throws JsonProcessingException If there is an error during JSON processing
     */
    public void saveDegreeChangesToDatabaseAndCache(Map<String, Object> degreesMap)
            throws JsonProcessingException {
        Map<String, Object> updateMap = new HashMap<>();
        String jsonString = new ObjectMapper().writeValueAsString(degreesMap);
        updateMap.put(Constants.FIELD_KEY, jsonString);
        updateMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, updateMap);
        redisCacheMgr.putCache(Constants.DEGREES_LIST, degreesMap);
    }

    /**
     * Updates or adds a degree to the list of degrees.
     *
     * @param degrees    The list of degrees to update
     * @param degreeName The degree name to add
     * @return true if the degree was added, false if it already existed
     */
    protected boolean updateOrAddDegree(List<String> degrees, String degreeName) {
        boolean exists = degrees.stream()
                .anyMatch(degree -> degree.equals(degreeName));
        if (!exists) {
            degrees.add(degreeName);
            Collections.sort(degrees);
            return true;
        }
        return false;
    }

    public ApiResponse searchDegree(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_SEARCH_DEGREE);
        try {
            logger.info("Searching degrees with criteria: {}", requestBody);
            if (!validationService.validateSearchRequest(apiResponse, requestBody)) {
                return apiResponse;
            }
            Map<String, Object> searchRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            EsResponse esResponse = searchMasterDataInIgotES(serverProperties.getEsDegreeIndexName(), serverProperties.getEsMasterDataIndexDocType(), searchRequest);

            if (!esResponse.isSuccess()) {
                ProjectUtil.errorResponse(apiResponse, esResponse.getMessage(), HttpStatus.BAD_REQUEST);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, esResponse.getData());
            apiResponse.getResult().put(Constants.COUNT, esResponse.getCount());

        } catch (IllegalArgumentException e) {
            logger.error("Invalid pagination or sorting parameters: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Invalid pagination or sorting parameters", HttpStatus.BAD_REQUEST);
            return apiResponse;

        } catch (Exception e) {
            logger.error("Unexpected error during degree search: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error during degree search", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }

        return apiResponse;
    }

    public ApiResponse searchInstitute(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_SEARCH_INSTITUTE);

        try {
            logger.info("Searching institutes with criteria: {}", requestBody);
            if (!validationService.validateSearchRequest(apiResponse, requestBody)) {
                return apiResponse;
            }
            Map<String, Object> searchRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            EsResponse esResponse = searchMasterDataInIgotES(serverProperties.getEsInstituteIndexName(), serverProperties.getEsMasterDataIndexDocType(), searchRequest);

            if (!esResponse.isSuccess()) {
                ProjectUtil.errorResponse(apiResponse, esResponse.getMessage(), HttpStatus.BAD_REQUEST);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, esResponse.getData());
            apiResponse.getResult().put(Constants.COUNT, esResponse.getCount());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid pagination or sorting parameters: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Invalid pagination or sorting parameters", HttpStatus.BAD_REQUEST);
            return apiResponse;

        } catch (Exception e) {
            logger.error("Unexpected error during institute search: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error during institute search", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }
        return apiResponse;
    }

    public ApiResponse addDegree(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_ADD_DEGREE);

        try {
           if(!validationService.addDegreeValidation(apiResponse, requestBody)){
               return apiResponse;
           }
           Degree degree = mapRequestToDegree(requestBody);
            // ------------------ Check Existing ------------------
            Optional<Degree> existingDegreeOpt = degreeRepository.findByNameIgnoreCase(degree.getName());
            if (existingDegreeOpt.isPresent()) {
                Degree existingDegree = existingDegreeOpt.get();
                if (existingDegree.getStatus() == 0) {
                    // Reactivate inactive record in Postgres
                    existingDegree.setStatus(1);
                    if (StringUtils.isNotEmpty(degree.getDescription())) {
                        existingDegree.setDescription(degree.getDescription());
                    }
                    Degree updated = degreeRepository.save(existingDegree);
                    // Sync ES
                    try {
                        EsResponse esResponse = esUtilService.saveObjectInIgotES(updated, serverProperties.getEsDegreeIndexName(), serverProperties.getEsMasterDataIndexDocType(), updated.getId().toString());
                        if (!esResponse.isSuccess()) {
                            logger.warn("Failed to index reactivated degree in ES: {}", esResponse.getMessage());
                        }
                    } catch (Exception e) {
                        logger.warn("Exception while indexing reactivated degree in ES: {}", e.getMessage(), e);
                    }
                    apiResponse.getResult().put(Constants.RESULT, updated);
                    return apiResponse;
                }
                ProjectUtil.errorResponse(apiResponse, "Degree already exists and is active", HttpStatus.CONFLICT);
                return apiResponse;
            }
            // ------------------ Save to Postgres FIRST ------------------
            degree.setStatus(1);
            Degree savedDegree = degreeRepository.save(degree);
            // ------------------ Save to Elasticsearch SECOND ------------------
            EsResponse esResponse = esUtilService.saveObjectInIgotES(savedDegree, serverProperties.getEsDegreeIndexName(), serverProperties.getEsMasterDataIndexDocType(), savedDegree.getId().toString());
            if (!esResponse.isSuccess()) {
                logger.error("Failed to index degree in ES: {} — Rolling back DB", esResponse.getMessage());
                // Rollback Postgres
                degreeRepository.delete(savedDegree);
                ProjectUtil.errorResponse(apiResponse, "Failed to add degree (ES indexing failed)", HttpStatus.INTERNAL_SERVER_ERROR);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, savedDegree);
            return apiResponse;

        } catch (Exception e) {
            logger.error("Unexpected error while adding degree", e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error while adding degree", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }
    }

    private Degree mapRequestToDegree(Map<String, Object> requestBody){
        Map<String, Object> request = (Map<String, Object>) requestBody.get(Constants.REQUEST);
        Degree degree = new Degree();
        degree.setName((String) request.get(Constants.NAME));
        degree.setDescription((String) request.get(Constants.DESCRIPTION));
        return degree;
    }

    public ApiResponse addInstitute(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        try {
            if(!validationService.addInstituteValidation(apiResponse, requestBody)){
                return apiResponse;
            }
            Institute institute = mapRequestToInstitute(requestBody);
            Optional<Institute> existingOpt = instituteRepository.findByNameIgnoreCase(institute.getName());
            if (existingOpt.isPresent()) {
                Institute existing = existingOpt.get();
                // If inactive → reactivate
                if (existing.getStatus() == 0) {
                    existing.setStatus(1);
                    if (StringUtils.isNotEmpty(institute.getDescription())) {
                        existing.setDescription(institute.getDescription());
                    }

                    Institute updated = instituteRepository.save(existing);
                    // Sync ES (best effort — reactivation generally should not rollback DB)
                    try {
                        EsResponse esResponse = esUtilService.saveObjectInIgotES(updated, serverProperties.getEsInstituteIndexName(), serverProperties.getEsMasterDataIndexDocType(), updated.getId().toString());
                        if (!esResponse.isSuccess()) {
                            logger.warn("Failed to index reactivated degree in ES: {}", esResponse.getMessage());
                        }
                    } catch (Exception e) {
                        logger.warn("Exception while indexing reactivated degree in ES: {}", e.getMessage(), e);
                    }
                    apiResponse.getResult().put(Constants.RESULT, updated);
                    return apiResponse;
                }
                // Active already
                ProjectUtil.errorResponse(apiResponse, "Institute already exists and is active", HttpStatus.CONFLICT);
                return apiResponse;
            }
            // ----------------- SAVE TO POSTGRES -----------------
            institute.setStatus(1);
            Institute saved = instituteRepository.save(institute);

            // ----------------- SAVE TO ELASTICSEARCH -----------------
            EsResponse esResponse = esUtilService.saveObjectInIgotES(saved, serverProperties.getEsInstituteIndexName(), serverProperties.getEsMasterDataIndexDocType(), saved.getId().toString());
            // If ES fails → rollback Postgres
            if (!esResponse.isSuccess()) {
                logger.error("Failed to index institute in ES: {} — Rolling back DB", esResponse.getMessage());
                instituteRepository.delete(saved);
                ProjectUtil.errorResponse(apiResponse, "Failed to add the institute (ES indexing failed)", HttpStatus.INTERNAL_SERVER_ERROR);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, saved);
            return apiResponse;

        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation while adding institute: {}", e.getMessage());
            ProjectUtil.errorResponse(apiResponse, "Invalid or duplicate institute data", HttpStatus.BAD_REQUEST);
            return apiResponse;

        } catch (Exception e) {
            logger.error("Unexpected error while adding institute: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error while adding institute", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }
    }

    private Institute mapRequestToInstitute(Map<String, Object> requestBody){
        Map<String, Object> request = (Map<String, Object>) requestBody.get(Constants.REQUEST);
        Institute institute = new Institute();
        institute.setName((String) request.get(Constants.NAME));
        institute.setDescription((String) request.get(Constants.DESCRIPTION));
        return institute;
    }

    public ApiResponse toggleDegreeStatus(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_DEGREE_STATUS);
        try {
            if (!validationService.validateUpdateStatusRequest(apiResponse, requestBody)) {
                return apiResponse;
            }
            Map<String, Object> statusUpdateRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            String degreeName = (String) statusUpdateRequest.get(Constants.NAME);
            int status = (statusUpdateRequest.get(Constants.STATUS) instanceof Number n) ? n.intValue() : Integer.parseInt(statusUpdateRequest.get(Constants.STATUS).toString());
            // Find degree by name
            Optional<Degree> optionalDegree = degreeRepository.findByNameIgnoreCase(degreeName);
            if (optionalDegree.isEmpty()) {
                ProjectUtil.errorResponse(apiResponse, "Degree not found", HttpStatus.NOT_FOUND);
                return apiResponse;
            }
            Degree degree = optionalDegree.get();
            // Check if already in desired state
            if ((status == 1 && degree.getStatus() == 1) || (status == 0 && degree.getStatus() == 0)) {
                String stateMsg = status == 1 ? "active" : "inactive";
                ProjectUtil.errorResponse(apiResponse, "Degree is already " + stateMsg, HttpStatus.BAD_REQUEST);
                return apiResponse;
            }
            // Update status
            degree.setStatus(status);
            Degree updatedDegree = degreeRepository.save(degree);

            // Push to IGOT ES
            EsResponse esResponse = esUtilService.saveObjectInIgotES(
                    updatedDegree,
                    serverProperties.getEsDegreeIndexName(),
                    serverProperties.getEsMasterDataIndexDocType(),
                    updatedDegree.getId().toString()
            );
            if (!esResponse.isSuccess()) {
                logger.warn("Failed to index degree in ES: {}. Postgres record remains intact.", esResponse.getMessage());
                ProjectUtil.errorResponse(apiResponse, "Degree status updated in DB but failed to sync with ES", HttpStatus.INTERNAL_SERVER_ERROR);
                apiResponse.getResult().put(Constants.RESULT, updatedDegree); // optionally return DB entity
                return apiResponse;
            }
            logger.info("Degree '{}' {} successfully", degreeName, status == 1 ? "activated" : "deactivated");
            apiResponse.getResult().put(Constants.RESULT, updatedDegree);
        } catch (Exception e) {
            logger.error("Unexpected error while updating degree status : {}", e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error while updating degree status", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return apiResponse;
    }

    public ApiResponse toggleInstituteStatus(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_INSTITUTE_STATUS);
        try {
            if (!validationService.validateUpdateStatusRequest(apiResponse, requestBody)) {
                return apiResponse;
            }
            Map<String, Object> statusUpdateRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            // Find institute by name
            String instituteName = statusUpdateRequest.get(Constants.NAME).toString();
            int status = (statusUpdateRequest.get(Constants.STATUS) instanceof Number n) ? n.intValue() : Integer.parseInt(statusUpdateRequest.get(Constants.STATUS).toString());
            Optional<Institute> optionalInstitute = instituteRepository.findByNameIgnoreCase(instituteName);
            if (optionalInstitute.isEmpty()) {
                ProjectUtil.errorResponse(apiResponse, "Institute not found", HttpStatus.NOT_FOUND);
                return apiResponse;
            }
            Institute institute = optionalInstitute.get();
            // Check if already in desired state
            if ((status == 1 && institute.getStatus() == 1) || (status == 0 && institute.getStatus() == 0)) {
                String stateMsg = status == 1 ? "active" : "inactive";
                ProjectUtil.errorResponse(apiResponse, "Institute is already " + stateMsg, HttpStatus.BAD_REQUEST);
                return apiResponse;
            }
            // Update status
            institute.setStatus(status);
            Institute updatedInstitute = instituteRepository.save(institute);
            // Push to IGOT ES
            EsResponse esResponse = esUtilService.saveObjectInIgotES(
                    updatedInstitute,
                    serverProperties.getEsInstituteIndexName(),
                    serverProperties.getEsMasterDataIndexDocType(),
                    updatedInstitute.getId().toString()
            );
            if (!esResponse.isSuccess()) {
                logger.warn("Failed to index institute in ES: {}. Postgres record remains intact.", esResponse.getMessage());
                ProjectUtil.errorResponse(apiResponse, "Institute status updated in DB but failed to sync with ES", HttpStatus.INTERNAL_SERVER_ERROR);
                apiResponse.getResult().put(Constants.RESULT, updatedInstitute); // optional: still return DB entity
                return apiResponse;
            }
            logger.info("Institute '{}' {} successfully", instituteName, status == 1 ? "activated" : "deactivated");
            apiResponse.getResult().put(Constants.RESULT, updatedInstitute);

        } catch (Exception e) {
            logger.error("Unexpected error while updating institute status", e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error while updating institute status", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return apiResponse;
    }

    public EsResponse searchMasterDataInIgotES(String indexName, String docType, Map<String, Object> searchRequest) {
        try {
            Object pageObj = searchRequest.get(Constants.PAGE_NUMBER);
            Object sizeObj = searchRequest.get(Constants.PAGE_SIZE);
            int page = (pageObj instanceof Number n) ? n.intValue() : 0;      // default = 0
            int size = (sizeObj instanceof Number n) ? n.intValue() : 20;     // default = 20
            int from = page * size;
            Object sortByObj = searchRequest.get(Constants.SORT_BY);
            String sortBy = (sortByObj != null) ? sortByObj.toString() : "id";
            if (sortBy.isEmpty()) sortBy = "id";
            if ("name".equals(sortBy)) {
                sortBy = "name.keyword";
            } else if ("description".equals(sortBy)) {
                sortBy = "description.keyword";
            }

            Object orderObj = searchRequest.get(Constants.ORDER_BY);
            String orderBy = (orderObj != null) ? orderObj.toString() : "ASC";
            boolean isDesc = "DESC".equalsIgnoreCase(orderBy);
            SortOrder sortOrder = isDesc ? SortOrder.DESC : SortOrder.ASC;

            SearchRequest esSearchRequest = new SearchRequest(indexName).types(docType);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                    .from(from)
                    .size(size)
                    .sort(sortBy, sortOrder);

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

            Object keywordObj = searchRequest.get(Constants.SEARCH_STRING);
            String keyword = (keywordObj != null) ? keywordObj.toString() : null;
            if (keyword != null && !keyword.isBlank()) {
                MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(keyword)
                        .field("name")
                        .field("name.ngram")
                        .field("description")
                        .field("description.ngram");

                boolQuery.must(multiMatch);
            } else {
                boolQuery.must(QueryBuilders.matchAllQuery());
            }

            Object statusObj = searchRequest.get(Constants.STATUS);
            Integer status = null;
            if (statusObj != null) {
                status = (statusObj instanceof Number n3) ? n3.intValue() : Integer.parseInt(statusObj.toString());
            }
            if (status == null) status = 1; // default active

            boolQuery.filter(QueryBuilders.termQuery("status", status));

            sourceBuilder.query(boolQuery);
            esSearchRequest.source(sourceBuilder);

            SearchResponse response = igotESClient.search(esSearchRequest, RequestOptions.DEFAULT);
            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                results.add(hit.getSourceAsMap());
            }

            return EsResponse.builder()
                    .success(true)
                    .message("Search successful")
                    .data(results)
                    .count(response.getHits().getTotalHits())
                    .build();

        } catch (Exception e) {
            return EsResponse.builder()
                    .success(false)
                    .message("Search failed: " + e.getMessage())
                    .build();
        }
    }
}