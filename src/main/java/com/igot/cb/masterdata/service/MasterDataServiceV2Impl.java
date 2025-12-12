package com.igot.cb.masterdata.service;

import com.igot.cb.masterdata.model.Degree;
import com.igot.cb.masterdata.model.Institute;
import com.igot.cb.masterdata.repository.DegreeRepository;
import com.igot.cb.masterdata.repository.InstituteRepository;
import com.igot.cb.transactional.elasticsearch.model.EsResponse;
import com.igot.cb.transactional.elasticsearch.service.EsUtilService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MasterDataServiceV2Impl implements MasterDataServiceV2 {

    public static final Logger logger = LoggerFactory.getLogger(MasterDataServiceImpl.class);
    private final DegreeRepository degreeRepository;
    private final InstituteRepository instituteRepository;
    private final ValidationService validationService;
    private final EsUtilService esUtilService;
    private final CbServerProperties serverProperties;
    private final RestHighLevelClient igotESClient;

    public MasterDataServiceV2Impl(
            DegreeRepository degreeRepository,
            InstituteRepository instituteRepository,
            ValidationService validationService,
            EsUtilService esUtilService,
            CbServerProperties serverProperties,
            @Qualifier("igotESClient") RestHighLevelClient igotESClient
    ) {
        this.degreeRepository = degreeRepository;
        this.instituteRepository = instituteRepository;
        this.validationService = validationService;
        this.esUtilService = esUtilService;
        this.serverProperties = serverProperties;
        this.igotESClient = igotESClient;
    }

    public ApiResponse searchMasterData(Map<String, Object> requestBody) {

        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_SEARCH_MASTER_DATA);
        try {
            logger.info("Searching master data: {}", requestBody);
            // Validate request
            if (!validationService.validateSearchRequest(apiResponse, requestBody)) {
                return apiResponse;
            }
            // Get type: degree / institute
            String type = (String) requestBody.get(Constants.TYPE);
            if (StringUtils.isBlank(type) || !serverProperties.getMasterDataAllowedType().contains(type)) {
                ProjectUtil.errorResponse(apiResponse, "Invalid type.", HttpStatus.BAD_REQUEST);
                return apiResponse;
            }

            // Select index dynamically
            String indexName = type.equalsIgnoreCase("degree")
                    ? serverProperties.getEsDegreeIndexName()
                    : serverProperties.getEsInstituteIndexName();

            Map<String, Object> searchRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);

            EsResponse esResponse = searchMasterDataInIgotES(
                    indexName,
                    serverProperties.getEsMasterDataIndexDocType(),
                    searchRequest);

            if (!esResponse.isSuccess()) {
                ProjectUtil.errorResponse(apiResponse, esResponse.getMessage(), HttpStatus.BAD_REQUEST);
                return apiResponse;
            }

            // Prepare response
            apiResponse.getResult().put(Constants.RESULT, esResponse.getData());
            apiResponse.getResult().put(Constants.COUNT, esResponse.getCount());

        } catch (IllegalArgumentException e) {
            logger.error("Invalid pagination or sorting parameters: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Invalid pagination or sorting parameters", HttpStatus.BAD_REQUEST);
            return apiResponse;

        } catch (Exception e) {
            logger.error("Unexpected error during search: {}", e.getMessage(), e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error during search", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }

        return apiResponse;
    }

    public ApiResponse upsertDegree(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_UPSERT_DEGREE);
        try {
            if (!validationService.upsertDegreeValidation(apiResponse, requestBody)) {
                return apiResponse;
            }
            Degree degree = mapRequestToDegree(requestBody);
            Map<String, Object> req = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            Object idObj = req.get(Constants.ID);
            // CASE 1: UPDATE (ID is present)
            if (ObjectUtils.isNotEmpty(idObj)) {
                Long id = Long.valueOf(idObj.toString());
                Optional<Degree> existingById = degreeRepository.findById(id);
                if (existingById.isEmpty()) {
                    ProjectUtil.errorResponse(apiResponse, "Degree with given ID not found", HttpStatus.NOT_FOUND);
                    return apiResponse;
                }
                Degree existing = existingById.get();
                // Update fields
                if (StringUtils.isNotBlank(degree.getName())) {
                    Optional<Degree> duplicate = degreeRepository.findByNameIgnoreCase(degree.getName());
                    if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                        ProjectUtil.errorResponse(apiResponse, "Degree already exists with the name: " + degree.getName(), HttpStatus.CONFLICT);
                        return apiResponse;
                    }
                    existing.setName(degree.getName());
                }
                if (StringUtils.isNotBlank(degree.getDescription())) {
                    existing.setDescription(degree.getDescription());
                }
                if (degree.getStatus() != null) {
                    existing.setStatus(degree.getStatus());
                }
                existing.setUpdatedOn(LocalDateTime.now());
                Degree updated = degreeRepository.save(existing);
                EsResponse esResponse = esUtilService.saveObjectInIgotES(updated, serverProperties.getEsDegreeIndexName(), serverProperties.getEsMasterDataIndexDocType(), updated.getId().toString());
                if (!esResponse.isSuccess()) {
                    logger.error("Failed to update institute in ES: {}", esResponse.getMessage());
                }
                apiResponse.getResult().put(Constants.RESULT, updated);
                return apiResponse;
            }
            // CASE 2: CREATE (ID is missing)
            Optional<Degree> existingByName = degreeRepository.findByNameIgnoreCase(degree.getName());
            if (existingByName.isPresent()) {
                ProjectUtil.errorResponse(apiResponse, "Degree already exists with the name: " + degree.getName(), HttpStatus.CONFLICT);
                return apiResponse;
            }
            // Create new
            degree.setStatus(1);
            Degree saved = degreeRepository.save(degree);
            EsResponse esResponse = esUtilService.saveObjectInIgotES(saved, serverProperties.getEsDegreeIndexName(), serverProperties.getEsMasterDataIndexDocType(), saved.getId().toString());
            if (!esResponse.isSuccess()) {
                degreeRepository.delete(saved);
                ProjectUtil.errorResponse(apiResponse, "Failed to add degree (ES indexing failed)", HttpStatus.INTERNAL_SERVER_ERROR);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, saved);
            return apiResponse;

        } catch (Exception e) {
            logger.error("Unexpected error in upsertDegree", e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error while adding/updating degree", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }
    }

    private Degree mapRequestToDegree(Map<String, Object> requestBody) {
        Map<String, Object> request = (Map<String, Object>) requestBody.get(Constants.REQUEST);
        Degree degree = new Degree();
        Object idObj = request.get(Constants.ID);
        degree.setId(idObj == null ? null : Long.valueOf(idObj.toString()));
        degree.setName((String) request.get(Constants.NAME));
        degree.setDescription((String) request.get(Constants.DESCRIPTION));
        degree.setStatus((Integer) request.get(Constants.STATUS));
        return degree;
    }

    public ApiResponse upsertInstitute(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_UPSERT_INSTITUTE);
        try {
            if (!validationService.upsertInstituteValidation(apiResponse, requestBody)) {
                return apiResponse;
            }
            Map<String, Object> req = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            Institute institute = mapRequestToInstitute(requestBody);
            Object idObj = req.get(Constants.ID);
            // CASE 1: UPDATE (ID PRESENT)
            if (ObjectUtils.isNotEmpty(idObj)) {
                Long id = Long.parseLong(idObj.toString());
                Optional<Institute> dbOpt = instituteRepository.findById(id);
                if (dbOpt.isEmpty()) {
                    ProjectUtil.errorResponse(apiResponse, "Institute with given ID not found", HttpStatus.NOT_FOUND);
                    return apiResponse;
                }
                Institute existing = dbOpt.get();
                // Update allowed fields
                if (StringUtils.isNotBlank(institute.getName())) {
                    Optional<Institute> duplicate = instituteRepository.findByNameIgnoreCase(institute.getName());
                    if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                        ProjectUtil.errorResponse(apiResponse, "Institute already exists with the name: " + institute.getName(), HttpStatus.CONFLICT);
                        return apiResponse;
                    }
                    existing.setName(institute.getName());
                }
                if (StringUtils.isNotBlank(institute.getDescription())) {
                    existing.setDescription(institute.getDescription());
                }
                if (institute.getStatus() != null) {
                    existing.setStatus(institute.getStatus());
                }
                existing.setUpdatedOn(LocalDateTime.now());
                // Save Postgres
                Institute updated = instituteRepository.save(existing);
                // Save ES
                EsResponse esResponse = esUtilService.saveObjectInIgotES(
                        updated,
                        serverProperties.getEsInstituteIndexName(),
                        serverProperties.getEsMasterDataIndexDocType(),
                        updated.getId().toString());

                if (!esResponse.isSuccess()) {
                    logger.error("Failed to update institute in ES: {}", esResponse.getMessage());
                }
                apiResponse.getResult().put(Constants.RESULT, updated);
                return apiResponse;
            }
            // CASE 2: CREATE NEW (ID NOT PRESENT)
            Optional<Institute> existingByName = instituteRepository.findByNameIgnoreCase(institute.getName());
            if (existingByName.isPresent()) {
                ProjectUtil.errorResponse(apiResponse, "Institute already exists with the name: " + institute.getName(), HttpStatus.CONFLICT);
                return apiResponse;
            }
            institute.setStatus(1);
            Institute saved = instituteRepository.save(institute);
            // Save ES
            EsResponse esResponse = esUtilService.saveObjectInIgotES(
                    saved,
                    serverProperties.getEsInstituteIndexName(),
                    serverProperties.getEsMasterDataIndexDocType(),
                    saved.getId().toString());

            if (!esResponse.isSuccess()) {
                logger.error("Failed to index institute in ES: {} — Rolling back DB", esResponse.getMessage());
                instituteRepository.delete(saved);
                ProjectUtil.errorResponse(apiResponse, "Failed to add institute (ES indexing failed)", HttpStatus.INTERNAL_SERVER_ERROR);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, saved);
            return apiResponse;
        } catch (Exception e) {
            logger.error("Unexpected error while adding/updating institute", e);
            ProjectUtil.errorResponse(apiResponse, "Unexpected error while adding/updating institute", HttpStatus.INTERNAL_SERVER_ERROR);
            return apiResponse;
        }
    }

    private Institute mapRequestToInstitute(Map<String, Object> requestBody) {
        Map<String, Object> request = (Map<String, Object>) requestBody.get(Constants.REQUEST);
        Institute institute = new Institute();
        Object idObj = request.get(Constants.ID);
        institute.setId(idObj == null ? null : Long.valueOf(idObj.toString()));
        institute.setName((String) request.get(Constants.NAME));
        institute.setDescription((String) request.get(Constants.DESCRIPTION));
        institute.setStatus((Integer) request.get(Constants.STATUS));
        return institute;
    }

    public EsResponse searchMasterDataInIgotES(String indexName, String docType, Map<String, Object> searchRequest) {
        try {
            int page = ((Number) searchRequest.getOrDefault(Constants.PAGE_NUMBER, 0)).intValue();
            int size = ((Number) searchRequest.getOrDefault(Constants.PAGE_SIZE, 20)).intValue();
            int from = page * size;

            // Sorting fields
            String sortBy = String.valueOf(searchRequest.getOrDefault(Constants.SORT_BY, "id"));
            if (sortBy.isEmpty()) sortBy = "id";
            if ("name".equals(sortBy)) sortBy = "name.keyword";
            if ("description".equals(sortBy)) sortBy = "description.keyword";

            String orderBy = String.valueOf(searchRequest.getOrDefault(Constants.ORDER_BY, "ASC"));
            boolean isDesc = "DESC".equalsIgnoreCase(orderBy);
            SortOrder sortOrder = isDesc ? SortOrder.DESC : SortOrder.ASC;

            // Build ES request
            SearchRequest esSearch = new SearchRequest(indexName).types(docType);
            SearchSourceBuilder source = new SearchSourceBuilder()
                    .from(from)
                    .size(size)
                    .sort(sortBy, sortOrder);

            BoolQueryBuilder bool = QueryBuilders.boolQuery();

            // ----------- SEARCH STRING (optional) -----------
            String keyword = (String) searchRequest.get(Constants.SEARCH_STRING);
            if (keyword != null && !keyword.isBlank()) {
                bool.must(QueryBuilders.multiMatchQuery(keyword)
                        .field("name")
                        .field("name.ngram")
                        .field("description")
                        .field("description.ngram"));
            } else {
                bool.must(QueryBuilders.matchAllQuery());
            }

            // ----------- EXACT MATCH FILTERS -----------
            Map<String, Object> filters = (Map<String, Object>) searchRequest.get("filters");
            if (MapUtils.isNotEmpty(filters)) {
                if (ObjectUtils.isNotEmpty(filters.get("id"))) {
                    bool.filter(QueryBuilders.termQuery("id", filters.get("id")));
                }
                if (ObjectUtils.isNotEmpty(filters.get("name"))) {
                    bool.filter(QueryBuilders.termQuery("name.keyword", filters.get("name")));
                }
                if (ObjectUtils.isNotEmpty(filters.get("status"))) {
                    Object statObj = filters.get("status");
                    int stat = (statObj instanceof Number n)
                            ? n.intValue()
                            : Integer.parseInt(statObj.toString());

                    bool.filter(QueryBuilders.termQuery("status", stat));
                }
            } else {
                bool.filter(QueryBuilders.termQuery("status", 1));
            }
            source.query(bool);
            esSearch.source(source);
            SearchResponse response = igotESClient.search(esSearch, RequestOptions.DEFAULT);
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
