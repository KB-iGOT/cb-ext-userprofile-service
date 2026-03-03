package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.AchievementService;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/learner/achievement")
public class AchievementController {

    @Autowired
    private AchievementService achievementService;

    @PostMapping("/create")
    public ResponseEntity<?> createLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestHeader(value = Constants.X_AUTH_USER_ORG_ID, required = true) String rootOrgId,
            @RequestBody Map<String, Object> request){
        ApiResponse response = achievementService.createLearnerAchievement(request, authToken, rootOrgId);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestHeader(value = Constants.X_AUTH_USER_ORG_ID, required = true) String rootOrgId,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.updateLearnerAchievement(request, authToken, rootOrgId);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{achievementId}")
    public ResponseEntity<Object> readLearnerAchievement(@PathVariable(Constants.ACHIEVEMENT_ID) String achievementId,
                                                         @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
                                                         @RequestHeader(value = Constants.CONTEXT_TYPE, required = true) String contextType) {
        ApiResponse response = achievementService.readLearnerAchievement(achievementId, authToken, contextType);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) throws Exception {
        ApiResponse response = achievementService.deleteLearnerAchievement(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/status/update")
    public ResponseEntity<?> statusUpdateLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = false) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchLearnerAchievements(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody SearchCriteria searchCriteria) {
        ApiResponse response = achievementService.searchLearnerAchievements(searchCriteria, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/list")
    public ResponseEntity<?> listLearnerAchievements(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = achievementService.getUserAchievements(authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

}
