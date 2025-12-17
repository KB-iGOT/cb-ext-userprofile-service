package com.igot.cb.transactional.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.elasticsearch.model.EsResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.update.UpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;



@Service
@Slf4j
public class EsUtilServiceImpl implements EsUtilService {
    private final RestHighLevelClient sbESClient;
    private final RestHighLevelClient igotESClient;
    private final CbServerProperties cbProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public EsUtilServiceImpl(@Qualifier("sbESClient") RestHighLevelClient sbESClient, @Qualifier("igotESClient") RestHighLevelClient igotESClient, CbServerProperties cbProperties,ObjectMapper objectMapper) {
        this.sbESClient = sbESClient;
        this.igotESClient = igotESClient;
        this.cbProperties = cbProperties;
        this.objectMapper = objectMapper;
    }

    public Boolean updateUserOrgCustomFields(String userId, String orgId, List<Map<String, Object>> orgCustomFields) {
        try {
            Map<String, Object> updateDoc = new HashMap<>();
            updateDoc.put(Constants.ORG_CUSTOM_FIELDS, orgCustomFields);

            UpdateRequest updateRequest = new UpdateRequest(cbProperties.getUserProfileIndex(), Constants.INDEX_TYPE, userId)
                    .doc(updateDoc)
                    .docAsUpsert(true)
                    .retryOnConflict(5);

            sbESClient.update(updateRequest, RequestOptions.DEFAULT);
            log.info("Updated orgCustomFields for userId: {} orgId: {}", userId, orgId);
            return true;
        } catch (Exception e) {
            log.error("Failed to update orgCustomFields for userId: {} orgId: {}", userId, orgId, e);
            return false;
        }
    }

    public EsResponse saveObjectInIgotES(Object doc, String indexName, String docType, String docId) {
        if (ObjectUtils.isEmpty(doc)) {
            return EsResponse.builder().success(false).message("Document object is null").build();
        }
        if (StringUtils.isEmpty(docId)) {
            return EsResponse.builder().success(false).message("Document ID must not be null or empty").build();
        }
        try {
            Map<String, Object> docMap = objectMapper.convertValue(doc, Map.class);
            UpdateRequest updateRequest = new UpdateRequest(indexName, docType, docId).doc(docMap).docAsUpsert(true);
            igotESClient.update(updateRequest, RequestOptions.DEFAULT);
            log.info("Document upserted successfully in IGOT ES, index [{}], type [{}], id [{}]", indexName, docType, docId);
            return EsResponse.builder()
                    .success(true)
                    .message("Document upserted successfully")
                    .documentId(docId)
                    .build();
        } catch (Exception e) {
            log.error("Error upserting document in IGOT ES, index [{}]: {}", indexName, e.getMessage(), e);
            return EsResponse.builder()
                    .success(false)
                    .message("Error upserting document: " + e.getMessage())
                    .build();
        }
    }
}
