package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.ProfileService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/user/profile")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    @PostMapping("/extended")
    public ResponseEntity<?> saveExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) throws Exception {
        ApiResponse response = profileService.saveExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }


    @GetMapping("/extended/{userId}")
    public ResponseEntity<Object> getExtendedProfileSummary(@PathVariable("userId") String userId, @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getExtendedProfileSummary(userId, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/{userId}/serviceHistory")
    public ResponseEntity<Object> getServiceHistory(@PathVariable("userId") String userId, @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId,"serviceHistory",authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/{userId}/education")
    public ResponseEntity<Object> getEducationalQualifications(@PathVariable("userId") String userId, @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId,"educationalQualifications",authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/{userId}/achievements")
    public ResponseEntity<Object> getAchievements(@PathVariable("userId") String userId, @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId,"achievements",authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @PutMapping("/extended")
    public ResponseEntity<?> updateExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) throws Exception {
        ApiResponse response = profileService.updateExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/extended")
    public ResponseEntity<?> deleteExtendedProfile(@RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
                                                   @RequestBody Map<String, Object> request) {

        ApiResponse response = profileService.deleteExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/basic/{userId}")
    public ResponseEntity<Object> getBasicProfile(@PathVariable("userId") String userId, @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getBasicProfile(userId,authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }


}
