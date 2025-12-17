package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;

import java.util.Map;

public interface MasterDataServiceV2 {

    ApiResponse searchMasterData(Map<String, Object> request);
    ApiResponse upsertDegree(Map<String, Object> requestBody);
    ApiResponse upsertInstitute(Map<String, Object> requestBody);
}
