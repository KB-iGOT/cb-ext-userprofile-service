package com.igot.cb.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ProfileService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
 class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
     void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController).build();
    }

    @Test
     void testSaveExtendedProfile() throws Exception {

        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("field1", "value1");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("SAVE_EXTENDED_PROFILE");
        when(profileService.saveExtendedProfile((request), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(post("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).saveExtendedProfile((request), (authToken));
    }

    @Test
     void testGetExtendedProfileSummary() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EXTENDED_PROFILE");

        when(profileService.getExtendedProfileSummary((userId), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/all/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).getExtendedProfileSummary((userId), (authToken));
    }


    @Test
     void testGetServiceHistory() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_SERVICE_HISTORY");

        when(profileService.readFullExtendedProfile((userId), (Constants.SERVICE_HISTORY), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/serviceHistory/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.SERVICE_HISTORY), (authToken));
    }

    @Test
     void testGetEducationalQualifications() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EDUCATION");

        when(profileService.readFullExtendedProfile((userId), (Constants.EDUCATION_QUALIFICATION), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/education/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.EDUCATION_QUALIFICATION), (authToken));
    }

    @Test
     void testGetLocationDetails() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_LOCATION");

        when(profileService.readFullExtendedProfile((userId), (Constants.LOCATION_DETAILS), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/locationDetails/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.LOCATION_DETAILS), (authToken));
    }

    @Test
     void testGetAchievements() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ACHIEVEMENTS");

        when(profileService.readFullExtendedProfile((userId), (Constants.ACHIEVEMENTS), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/achievements/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.ACHIEVEMENTS), (authToken));
    }

    @Test
     void testUpdateExtendedProfile() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("field1", "updatedValue");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("UPDATE_EXTENDED_PROFILE");

        when(profileService.updateExtendedProfile((request), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(put("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).updateExtendedProfile((request), (authToken));
    }

    @Test
     void testDeleteExtendedProfile() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("profileId", "123");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("DELETE_EXTENDED_PROFILE");

        when(profileService.deleteExtendedProfile((request), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(delete("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).deleteExtendedProfile((request), (authToken));
    }

    @Test
     void testGetBasicProfile() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_BASIC_PROFILE");

        when(profileService.getBasicProfile((userId), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/basic/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).getBasicProfile((userId), (authToken));
    }

    @Test
     void testGetCompetencies() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_COMPETENCIES");

        when(profileService.listCompetencies((userId), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/competencies/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).listCompetencies((userId), (authToken));
    }



}
