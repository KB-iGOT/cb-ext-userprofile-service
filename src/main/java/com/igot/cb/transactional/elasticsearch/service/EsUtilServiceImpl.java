package com.igot.cb.transactional.elasticsearch.service;

import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.elasticsearch.action.update.UpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;

import java.util.*;



@Service
@Slf4j
public class EsUtilServiceImpl implements EsUtilService {
    private final RestHighLevelClient sbESClient;
    private final CbServerProperties cbProperties;

    public EsUtilServiceImpl(RestHighLevelClient sbESClient, CbServerProperties cbProperties) {
        this.sbESClient = sbESClient;
        this.cbProperties = cbProperties;
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
}
