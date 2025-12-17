package com.igot.cb.transactional.elasticsearch.service;

import com.igot.cb.transactional.elasticsearch.model.EsResponse;

import java.util.List;
import java.util.Map;

public interface EsUtilService {
  Boolean updateUserOrgCustomFields(String userId, String orgId, List<Map<String, Object>> fields);
  public EsResponse saveObjectInIgotES(Object doc, String indexName, String docType, String docId);
}
