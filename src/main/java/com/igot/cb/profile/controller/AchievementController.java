package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.AchievementService;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/learner/achievement")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestHeader(value = Constants.X_AUTH_USER_ORG_ID) String rootOrgId,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.createLearnerAchievement(request, authToken, rootOrgId);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse> updateLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestHeader(value = Constants.X_AUTH_USER_ORG_ID) String rootOrgId,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.updateLearnerAchievement(request, authToken, rootOrgId);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{achievementId}")
    public ResponseEntity<ApiResponse> readLearnerAchievement(
            @PathVariable(Constants.ACHIEVEMENT_ID) String achievementId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestHeader(value = Constants.CONTEXT_TYPE) String contextType) {
        ApiResponse response = achievementService.readLearnerAchievement(achievementId, authToken, contextType);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> deleteLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.deleteLearnerAchievement(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/status/update")
    public ResponseEntity<ApiResponse> statusUpdateLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = false) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchLearnerAchievements(
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestBody SearchCriteria searchCriteria) {
        ApiResponse response = achievementService.searchLearnerAchievements(searchCriteria, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse> listLearnerAchievements(
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestParam(value = Constants.ID, required = false) String id) {
        ApiResponse response = achievementService.getUserAchievements(authToken, id);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/v2/list")
    public ResponseEntity<ApiResponse> listLearnerAchievementsByUserIds(
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.getUserAchievementsByUserIds(authToken, request);
        return ResponseEntity.status(response.getResponseCode()).body(response);
    }

}
