package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.ProfileService;
import com.igot.cb.util.Constants;

import org.igot.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/profile")
public class ProfileController {
    private ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/extended")
    public ResponseEntity<ApiResponse> saveExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = profileService.saveExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/extended/all/{userId}")
    public ResponseEntity<ApiResponse> getExtendedProfileSummary(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getExtendedProfileSummary(userId, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/serviceHistory/{userId}")
    public ResponseEntity<ApiResponse> getServiceHistory(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.SERVICE_HISTORY, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/education/{userId}")
    public ResponseEntity<ApiResponse> getEducationalQualifications(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.EDUCATION_QUALIFICATION,
                authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/locationDetails/{userId}")
    public ResponseEntity<ApiResponse> getLocationDetails(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.LOCATION_DETAILS, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/achievements/{userId}")
    public ResponseEntity<ApiResponse> getAchievements(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.ACHIEVEMENTS, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @PutMapping("/extended")
    public ResponseEntity<ApiResponse> updateExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = profileService.updateExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/extended")
    public ResponseEntity<ApiResponse> deleteExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) {

        ApiResponse response = profileService.deleteExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/basic/{userId}")
    public ResponseEntity<ApiResponse> getBasicProfile(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getBasicProfile(userId, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/extended/competencies/{userId}")
    public ResponseEntity<ApiResponse> getCompetencies(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.listCompetencies(userId, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @PostMapping(value = "/update/additionalFields")
    public ResponseEntity<ApiResponse> updateAdditionalFields(
            @RequestHeader(Constants.X_AUTH_TOKEN) String authToken,
            @RequestBody Map<String, Object> requestBody) {
        ApiResponse response = profileService.updateAdditionalFields(requestBody, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/getAdditionalFields/{userId}/{orgId}")
    public ResponseEntity<ApiResponse> getAdditionalFieldsByOrg(
            @PathVariable String userId,
            @PathVariable String orgId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken) {
        ApiResponse response = profileService.getAdditionalFieldsByOrg(userId, orgId, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
