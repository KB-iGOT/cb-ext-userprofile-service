package com.igot.cb.masterdata.controller;

import com.igot.cb.masterdata.service.MasterDataService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class MasterDataControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MasterDataService masterDataService;

    @InjectMocks
    private MasterDataController masterDataController;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(masterDataController).build();
    }

    @Test
    public void testGetInstitutionsList() throws Exception {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("TEST_API");
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
    public void testGetDegreesList() throws Exception {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("TEST_API");
        mockResponse.setResponseCode(HttpStatus.OK);
        when(masterDataService.getDegreesList(authToken)).thenReturn(mockResponse);
        mockMvc.perform(get("/v1/masterdata/list/degrees")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()));
        verify(masterDataService, times(1)).getDegreesList(authToken);
    }
}