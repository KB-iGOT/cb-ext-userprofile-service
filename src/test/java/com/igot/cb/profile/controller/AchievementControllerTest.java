package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.AchievementService;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void testListLearnerAchievements_success() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("achievements", "data");
        apiResponse.put("result", result);
        when(achievementService.getUserAchievements(anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievements("token");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token");
    }

    @Test
    void testListLearnerAchievements_unauthorized() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        when(achievementService.getUserAchievements(anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievements("invalid_token");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("invalid_token");
    }

    @Test
    void testListLearnerAchievements_badRequest() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievements(anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievements("token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token");
    }

    @Test
    void testListLearnerAchievements_internalServerError() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        when(achievementService.getUserAchievements(anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievements("token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token");
    }

    @Test
    void testListLearnerAchievements_emptyResult() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        apiResponse.put("result", new HashMap<>());
        when(achievementService.getUserAchievements(anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievements("token");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token");
    }

    // ==================== listLearnerAchievementsByUserIds TESTS ====================

    /**
     * Helper: builds a well-formed request body:
     * { "request": { "achievementIds": [...] } }
     */
    private Map<String, Object> buildBulkRequest(List<?> ids) {
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.ACHIEVEMENT_IDS, ids);
        Map<String, Object> body = new HashMap<>();
        body.put(Constants.REQUEST, inner);
        return body;
    }

    @Test
    void testListLearnerAchievementsByUserIds_success_multipleIds() {
        // valid request with two ids → extracted and forwarded to service as List<String>
        List<String> ids = List.of("achv-1", "achv-2");
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.getUserAchievementsByUserIds("token", ids)).thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", buildBulkRequest(ids));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(apiResponse, response.getBody());
        verify(achievementService).getUserAchievementsByUserIds("token", ids);
    }

    @Test
    void testListLearnerAchievementsByUserIds_success_singleId() {
        // single achievementId → list of one element passed to service
        List<String> ids = List.of("achv-1");
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.getUserAchievementsByUserIds("token", ids)).thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", buildBulkRequest(ids));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", ids);
    }

    @Test
    void testListLearnerAchievementsByUserIds_invalidToken_returnsUnauthorized() {
        // service validates the token and returns UNAUTHORIZED
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyList()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response = achievementController.listLearnerAchievementsByUserIds(
                "bad-token", buildBulkRequest(List.of("achv-1")));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertSame(apiResponse, response.getBody());
    }

    @Test
    void testListLearnerAchievementsByUserIds_emptyAchievementIdsList_returnsBadRequest() {
        // empty list is still a valid List → forwarded to service, service returns BAD_REQUEST
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievementsByUserIds("token", Collections.emptyList()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response = achievementController.listLearnerAchievementsByUserIds(
                "token", buildBulkRequest(Collections.emptyList()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", Collections.emptyList());
    }

    @Test
    void testListLearnerAchievementsByUserIds_missingRequestKey_passesNullToService() {
        // body has no "request" key → instanceof Map<?,?> check fails → achievementIds stays null
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievementsByUserIds(eq("token"), isNull()))
                .thenReturn(apiResponse);

        Map<String, Object> body = new HashMap<>(); // no "request" key

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", null);
    }

    @Test
    void testListLearnerAchievementsByUserIds_requestValueNotMap_passesNullToService() {
        // "request" value is a String, not a Map → instanceof Map<?,?> fails → null passed
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievementsByUserIds(eq("token"), isNull()))
                .thenReturn(apiResponse);

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.REQUEST, "not-a-map");

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", null);
    }

    @Test
    void testListLearnerAchievementsByUserIds_achievementIdsKeyMissing_passesNullToService() {
        // "request" map present but "achievementIds" key absent → instanceof List<?> fails → null
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievementsByUserIds(eq("token"), isNull()))
                .thenReturn(apiResponse);

        Map<String, Object> inner = new HashMap<>(); // no ACHIEVEMENT_IDS key
        Map<String, Object> body = new HashMap<>();
        body.put(Constants.REQUEST, inner);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", null);
    }

    @Test
    void testListLearnerAchievementsByUserIds_achievementIdsValueNotList_passesNullToService() {
        // "achievementIds" is a String instead of a List → instanceof List<?> fails → null
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievementsByUserIds(eq("token"), isNull()))
                .thenReturn(apiResponse);

        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.ACHIEVEMENT_IDS, "achv-1"); // String, not List
        Map<String, Object> body = new HashMap<>();
        body.put(Constants.REQUEST, inner);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", null);
    }

    @Test
    void testListLearnerAchievementsByUserIds_nonStringIds_convertedToString() {
        // List contains Integer objects → controller maps each via Object::toString before passing
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyList()))
                .thenReturn(apiResponse);

        List<Object> mixedIds = new ArrayList<>();
        mixedIds.add(123);         // Integer
        mixedIds.add("achv-str"); // String

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", buildBulkRequest(mixedIds));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", List.of("123", "achv-str"));
    }

    @Test
    void testListLearnerAchievementsByUserIds_serviceReturnsInternalServerError() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyList()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response = achievementController.listLearnerAchievementsByUserIds(
                "token", buildBulkRequest(List.of("achv-1")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertSame(apiResponse, response.getBody());
    }

    @Test
    void testListLearnerAchievementsByUserIds_responseBodyForwardedUnchanged() {
        // whatever the service returns must be forwarded as-is (no wrapping)
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        apiResponse.put(Constants.RESULT,
                Map.of(Constants.SEARCH_RESULTS,
                        Map.of(Constants.DATA, List.of(), Constants.TOTAL_COUNT, 0)));
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyList()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response = achievementController.listLearnerAchievementsByUserIds(
                "token", buildBulkRequest(List.of("achv-1")));

        assertSame(apiResponse, response.getBody());
    }
}
