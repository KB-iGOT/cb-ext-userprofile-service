package com.igot.cb.profile.service;

import com.igot.cb.util.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class AchievementServiceImpl implements AchievementService{

    @Override
    public ApiResponse createLearnerAchievement(Map<String, Object> request, String userToken) {
        return null;
    }

    @Override
    public ApiResponse updateLearnerAchievement(Map<String, Object> request, String userToken) {
        return null;
    }

    @Override
    public ApiResponse deleteLearnerAchievement(Map<String, Object> request, String userToken) {
        return null;
    }

    @Override
    public ApiResponse readLearnerAchievement(String userId, String userToken) {
        return null;
    }
}
