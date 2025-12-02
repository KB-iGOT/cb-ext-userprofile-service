package com.igot.cb.masterdata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.masterdata.service.MasterDataService;
import com.igot.cb.util.Constants;

import org.igot.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MasterDataControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MasterDataService masterDataService;

    @InjectMocks
    private MasterDataController masterDataController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(masterDataController).build();
    }

    @Test
    void testGetInstitutionsList() throws Exception {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = ApiResponse.createDefaultResponse("TEST_API");
        mockResponse.setResponseCode(HttpStatus.OK);
        when(masterDataService.getInstitutionsList(authToken)).thenReturn(mockResponse);
        mockMvc.perform(get("/v1/masterdata/list/institutions")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()));
        verify(masterDataService, times(1)).getInstitutionsList(authToken);
    }

    @Test
    void testGetDegreesList() throws Exception {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = ApiResponse.createDefaultResponse("TEST_API");
        mockResponse.setResponseCode(HttpStatus.OK);
        when(masterDataService.getDegreesList(authToken)).thenReturn(mockResponse);
        mockMvc.perform(get("/v1/masterdata/list/degrees")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()));
        verify(masterDataService, times(1)).getDegreesList(authToken);
    }

    @Test
    void testUpdateInstitution() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("institutionName", "Test Institution");
        ApiResponse mockResponse = ApiResponse.createDefaultResponse(Constants.API_UPDATE_INSTITUTION_LIST);
        mockResponse.setResponseCode(HttpStatus.CREATED);
        mockResponse.getResult().put("response", "Institution added successfully: Test Institution");
        when(masterDataService.updateInstitutionList(eq(authToken), any(Map.class))).thenReturn(mockResponse);
        mockMvc.perform(post("/v1/masterdata/update/institution")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.CREATED.name()))
                .andExpect(jsonPath("$.result.response").value("Institution added successfully: Test Institution"));

        verify(masterDataService, times(1)).updateInstitutionList(eq(authToken), any(Map.class));
    }

    @Test
    void testUpdateDegree() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("degreeName", "Test Degree");
        ApiResponse mockResponse = ApiResponse.createDefaultResponse(Constants.API_UPDATE_DEGREE_LIST);
        mockResponse.setResponseCode(HttpStatus.CREATED);
        mockResponse.getResult().put("response", "Degree added successfully: Test Degree");
        when(masterDataService.updateDegreesList(eq(authToken), any(Map.class))).thenReturn(mockResponse);
        mockMvc.perform(post("/v1/masterdata/update/degree")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.CREATED.name()))
                .andExpect(jsonPath("$.result.response").value("Degree added successfully: Test Degree"));
        verify(masterDataService, times(1)).updateDegreesList(eq(authToken), any(Map.class));
    }
}