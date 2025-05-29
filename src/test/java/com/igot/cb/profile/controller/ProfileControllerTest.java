package com.igot.cb.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ProfileService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController).build();
    }

    @Test
    public void testSaveExtendedProfile() throws Exception {

        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("field1", "value1");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("SAVE_EXTENDED_PROFILE");
        when(profileService.saveExtendedProfile(eq(request), eq(authToken))).thenReturn(mockResponse);

        mockMvc.perform(post("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).saveExtendedProfile(eq(request), eq(authToken));
    }

    @Test
    public void testGetExtendedProfileSummary() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EXTENDED_PROFILE");

        when(profileService.getExtendedProfileSummary(eq(userId), eq(authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/all/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).getExtendedProfileSummary(eq(userId), eq(authToken));
    }


    @Test
    public void testGetServiceHistory() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_SERVICE_HISTORY");

        when(profileService.readFullExtendedProfile(eq(userId), eq(Constants.SERVICE_HISTORY), eq(authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/serviceHistory/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile(eq(userId), eq(Constants.SERVICE_HISTORY), eq(authToken));
    }

    @Test
    public void testGetEducationalQualifications() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EDUCATION");

        when(profileService.readFullExtendedProfile(eq(userId), eq(Constants.EDUCATION_QUALIFICATION), eq(authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/education/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile(eq(userId), eq(Constants.EDUCATION_QUALIFICATION), eq(authToken));
    }

    @Test
    public void testGetLocationDetails() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_LOCATION");

        when(profileService.readFullExtendedProfile(eq(userId), eq(Constants.LOCATION_DETAILS), eq(authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/locationDetails/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile(eq(userId), eq(Constants.LOCATION_DETAILS), eq(authToken));
    }

    @Test
    public void testGetAchievements() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ACHIEVEMENTS");

        when(profileService.readFullExtendedProfile(eq(userId), eq(Constants.ACHIEVEMENTS), eq(authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/achievements/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile(eq(userId), eq(Constants.ACHIEVEMENTS), eq(authToken));
    }

    @Test
    public void testUpdateExtendedProfile() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("field1", "updatedValue");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("UPDATE_EXTENDED_PROFILE");

        when(profileService.updateExtendedProfile(eq(request), eq(authToken))).thenReturn(mockResponse);

        mockMvc.perform(put("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).updateExtendedProfile(eq(request), eq(authToken));
    }

    @Test
    public void testDeleteExtendedProfile() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("profileId", "123");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("DELETE_EXTENDED_PROFILE");

        when(profileService.deleteExtendedProfile(eq(request), eq(authToken))).thenReturn(mockResponse);

        mockMvc.perform(delete("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).deleteExtendedProfile(eq(request), eq(authToken));
    }

    @Test
    public void testGetBasicProfile() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_BASIC_PROFILE");

        when(profileService.getBasicProfile(eq(userId), eq(authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/basic/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).getBasicProfile(eq(userId), eq(authToken));
    }

    @Test
    public void testGetCompetencies() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_COMPETENCIES");

        when(profileService.listCompetencies(eq(userId), eq(authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/competencies/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).listCompetencies(eq(userId), eq(authToken));
    }



}
