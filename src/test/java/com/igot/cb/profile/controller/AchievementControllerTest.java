package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.AchievementService;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.util.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AchievementControllerTest {
    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private AchievementController achievementController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateLearnerAchievement() throws Exception {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.createLearnerAchievement(anyMap(), anyString(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.createLearnerAchievement("token", "orgId", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).createLearnerAchievement(request, "token", "orgId");
    }

    @Test
    void testUpdateLearnerAchievement() throws Exception {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.updateLearnerAchievement(anyMap(), anyString(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.updateLearnerAchievement("token", "orgId", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).updateLearnerAchievement(request, "token", "orgId");
    }

    @Test
    void testReadLearnerAchievement() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.readLearnerAchievement(anyString(), anyString(), anyString())).thenReturn(apiResponse);
        ResponseEntity<Object> response = achievementController.readLearnerAchievement("achId", "token", "contextType");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).readLearnerAchievement("achId", "token", "contextType");
    }

    @Test
    void testDeleteLearnerAchievement() throws Exception {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.deleteLearnerAchievement(anyMap(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.deleteLearnerAchievement("token", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).deleteLearnerAchievement(request, "token");
    }

    @Test
    void testStatusUpdateLearnerAchievement() {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.statusUpdateLearnerAchievement(anyMap(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.statusUpdateLearnerAchievement("token", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).statusUpdateLearnerAchievement(request, "token");
    }

    @Test
    void testSearchLearnerAchievements() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.searchLearnerAchievements(any(SearchCriteria.class), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.searchLearnerAchievements("token", searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).searchLearnerAchievements(searchCriteria, "token");
    }
}
