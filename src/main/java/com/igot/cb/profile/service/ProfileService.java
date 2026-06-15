package com.igot.cb.profile.service;

import com.igot.cb.util.ApiResponse;

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

    ApiResponse getVolunteerUserBasicProfile(Map<String, Object> request, String userToken);

    ApiResponse getVolunteerExtendedProfileSummary(Map<String, Object> request, String authToken);

    ApiResponse getVolunteerUserAdditionalFields(Map<String, Object> request, String authToken);
}
