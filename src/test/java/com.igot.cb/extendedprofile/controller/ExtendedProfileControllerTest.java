package com.igot.cb.extendedprofile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.extendedprofile.service.ExtendedProfileService;
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

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class ExtendedProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExtendedProfileService extendedProfileService;

    @InjectMocks
    private ExtendedProfileController extendedProfileController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(extendedProfileController).build();
    }

    @Test
    public void testGetStatesList() throws Exception {
        // Arrange
        String authToken = "test-auth-token";
        ApiResponse expectedResponse = ProjectUtil.createDefaultResponse("GET_STATES_LIST");
        expectedResponse.put("states", Map.of("Maharashtra", "MH", "Karnataka", "KA"));

        when(extendedProfileService.getStatesList(eq(authToken))).thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(get("/v1/extendedprofile/list/states")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Removed the incorrect path $.response.code
                .andExpect(jsonPath("$.result.states").exists());

        // Verify service method was called
        verify(extendedProfileService).getStatesList(eq(authToken));
    }

    @Test
    public void testGetDistrictsList() throws Exception {
        // Arrange
        String authToken = "test-auth-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("stateCode", "MH");

        ApiResponse expectedResponse = ProjectUtil.createDefaultResponse("GET_DISTRICTS_LIST");
        expectedResponse.put("districts", Map.of("Mumbai", "MUM", "Pune", "PUN"));

        when(extendedProfileService.getDistrictsList(eq(authToken), any(Map.class))).thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/v1/extendedprofile/list/districts")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.districts").exists());

        // Verify service method was called
        verify(extendedProfileService).getDistrictsList(eq(authToken), eq(requestBody));
    }
}