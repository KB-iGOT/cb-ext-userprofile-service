package com.igot.cb.profile.service;

import com.igot.cb.util.ApiResponse;

import java.util.Map;

public interface AchievementService {

    ApiResponse createLearnerAchievement(Map<String, Object> request, String userToken, String rootOrgId);

    ApiResponse updateLearnerAchievement(Map<String, Object> request, String userToken, String orgToken);

    ApiResponse deleteLearnerAchievement(Map<String, Object> request, String userToken);

    ApiResponse readLearnerAchievement(String achievementId, String userToken, String contextType);

    ApiResponse statusUpdateLearnerAchievement(Map<String, Object> request, String authToken);
}
