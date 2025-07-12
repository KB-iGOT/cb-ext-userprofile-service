package com.igot.cb.profile.service;

import com.igot.cb.util.ApiResponse;

import java.util.List;
import java.util.Map;

public interface ProfileService {

    ApiResponse saveExtendedProfile(Map<String, Object> request, String userToken);

    ApiResponse getExtendedProfileSummary(String userId, String userToken);

    ApiResponse readFullExtendedProfile(String userId, String contextType, String userToken);

    ApiResponse updateExtendedProfile(Map<String, Object> request, String userToken);

    ApiResponse deleteExtendedProfile(Map<String, Object> request, String userToken);

    ApiResponse getBasicProfile(String userId, String userToken);

    ApiResponse listCompetencies(String userId, String userToken);

    ApiResponse updateAdditionalFields(Map<String, Object> request, String userToken);

    ApiResponse getAdditionalFieldsByOrg(String userId, String orgId, String authToken);

    public Map<String, Object> readUserDataFromDB(String userId, List<String> keyList);
}
