package com.igot.cb.transactional.elasticsearch.service;

import java.util.List;
import java.util.Map;

public interface EsUtilService {
  Boolean updateUserOrgCustomFields(String userId, String orgId, List<Map<String, Object>> fields);
}
