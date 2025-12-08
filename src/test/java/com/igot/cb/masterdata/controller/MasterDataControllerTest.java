package com.igot.cb.masterdata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.masterdata.model.Degree;
import com.igot.cb.masterdata.model.Institute;
import com.igot.cb.masterdata.model.SearchCriteria;
import com.igot.cb.masterdata.model.StatusUpdateRequest;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
    void testGetDegreesList() throws Exception {
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

    @Test
    void testUpdateInstitution() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("institutionName", "Test Institution");
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_INSTITUTION_LIST);
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
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_DEGREE_LIST);
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

    @Test
    void testSearchDegree() throws Exception {
        Map<String, Object> requestBody = Map.of("request", Map.of("searchString", "B.Tech"));
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("search-degree");
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.getResult().put("degrees", List.of("B.Tech"));

        when(masterDataService.searchDegree(any(Map.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/v1/masterdata/degree/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()))
                .andExpect(jsonPath("$.result.degrees[0]").value("B.Tech"));

        verify(masterDataService, times(1)).searchDegree(any(Map.class));
    }

    @Test
    void testSearchInstitute() throws Exception {
        Map<String, Object> requestBody = Map.of("request", Map.of("searchString", "IIT"));
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("search-institute");
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.getResult().put("institutes", List.of("IIT"));
        when(masterDataService.searchInstitute(any(Map.class))).thenReturn(mockResponse);
        mockMvc.perform(post("/v1/masterdata/institute/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()))
                .andExpect(jsonPath("$.result.institutes[0]").value("IIT"));
        verify(masterDataService, times(1)).searchInstitute(any(Map.class));
    }

    @Test
    void testAddDegree_Success() throws Exception {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_ADD_DEGREE);
        apiResponse.getParams().setStatus(Constants.SUCCESS);

        when(masterDataService.addDegree(any())).thenReturn(apiResponse);

        mockMvc.perform(post("/v1/masterdata/add/degree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": {\"name\": \"MBA\", \"description\": \"Master\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.status").value(Constants.SUCCESS));
    }

    @Test
    void testAddDegree_Failure() throws Exception {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_ADD_DEGREE);
        apiResponse.getParams().setStatus(Constants.FAILED);

        when(masterDataService.addDegree(any())).thenReturn(apiResponse);

        mockMvc.perform(post("/v1/masterdata/add/degree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": {\"name\": \"MBA\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.status").value(Constants.FAILED));
    }

    private String requestJson() {
        return "{ \"request\": { \"name\": \"IIT Delhi\", \"description\": \"Engineering\" } }";
    }

    @Test
    void testAddInstitute_NewInstitute_Success() throws Exception {

        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        Institute result = new Institute();
        result.setId(10L);
        result.setName("IIT Delhi");
        result.setStatus(1);

        apiResponse.getResult().put(Constants.RESULT, result);

        when(masterDataService.addInstitute(any())).thenReturn(apiResponse);

        mockMvc.perform(post("/v1/masterdata/add/institute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.result.id").value(10))
                .andExpect(jsonPath("$.result.result.name").value("IIT Delhi"))
                .andExpect(jsonPath("$.result.result.status").value(1));
    }

    @Test
    void testAddInstitute_ValidationFails() throws Exception {

        ApiResponse resp = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        ProjectUtil.errorResponse(resp, "Invalid request", HttpStatus.BAD_REQUEST);

        when(masterDataService.addInstitute(any())).thenReturn(resp);

        mockMvc.perform(post("/v1/masterdata/add/institute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                // Fix JSON path to match actual field name: "errMsg"
                .andExpect(jsonPath("$.params.errMsg").value("Invalid request"));
    }


    @Test
    void testAddInstitute_ExistingActive_Conflict() throws Exception {

        ApiResponse resp = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        ProjectUtil.errorResponse(resp, "Institute already exists and is active", HttpStatus.CONFLICT);

        when(masterDataService.addInstitute(any())).thenReturn(resp);

        mockMvc.perform(post("/v1/masterdata/add/institute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andDo(print())
                .andExpect(status().isConflict())
                // Fix JSON path to match actual field name: "errMsg"
                .andExpect(jsonPath("$.params.errMsg")
                        .value("Institute already exists and is active"));
    }

    @Test
    void testAddInstitute_ExistingInactive_Reactivated() throws Exception {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        Institute result = new Institute();
        result.setId(5L);
        result.setName("IIT Delhi");
        result.setStatus(1); // reactivated

        apiResponse.getResult().put(Constants.RESULT, result);

        when(masterDataService.addInstitute(any())).thenReturn(apiResponse);

        mockMvc.perform(post("/v1/masterdata/add/institute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.result.id").value(5))
                .andExpect(jsonPath("$.result.result.status").value(1));
    }

    @Test
    void testAddInstitute_ESFailure_500() throws Exception {
        ApiResponse resp = ProjectUtil.createDefaultResponse(Constants.API_ADD_INSTITUTE);
        ProjectUtil.errorResponse(resp,
                "Failed to add the institute (ES indexing failed)",
                HttpStatus.INTERNAL_SERVER_ERROR);

        when(masterDataService.addInstitute(any())).thenReturn(resp);

        mockMvc.perform(post("/v1/masterdata/add/institute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                // Fix JSON path to match actual field name: "errMsg"
                .andExpect(jsonPath("$.params.errMsg")
                        .value("Failed to add the institute (ES indexing failed)"));
    }

    @Test
    void testUpdateDegreeStatus_Success() throws Exception {
        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("update-degree-status");
        mockResponse.getParams().setStatus("success");
        mockResponse.setResponseCode(HttpStatus.OK);
        when(masterDataService.toggleDegreeStatus(requestBody))
                .thenReturn(mockResponse);
        mockMvc.perform(put("/v1/masterdata/degree/update/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.status").value("success"));
        verify(masterDataService).toggleDegreeStatus(requestBody);
    }

    @Test
    void testUpdateDegreeStatus_Failure() throws Exception {
        Map<String, Object> requestBody = Map.of("request", Map.of("name", "MBA", "status", 1));
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("update-degree-status");
        mockResponse.getParams().setStatus("failure");
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        // Correct stubbing for method that accepts a Map
        when(masterDataService.toggleDegreeStatus(anyMap())).thenReturn(mockResponse);
        mockMvc.perform(put("/v1/masterdata/degree/update/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.status").value("failure"));
        // Correct verification for method that takes a Map
        verify(masterDataService).toggleDegreeStatus(anyMap());
    }

    @Test
    void testInstituteUpdateStatus_Success() throws Exception {
        Map<String, Object> requestBody = Map.of("request", Map.of("name", "IIT Delhi", "status", 1));
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("update-institute-status");
        mockResponse.getParams().setStatus("success");
        mockResponse.setResponseCode(HttpStatus.OK);
        // Correct mocking for Map-based method
        when(masterDataService.toggleInstituteStatus(requestBody))
                .thenReturn(mockResponse);

        mockMvc.perform(put("/v1/masterdata/institute/update/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.status").value("success"));

        // Verify correct service call
        verify(masterDataService).toggleInstituteStatus(requestBody);
    }

}