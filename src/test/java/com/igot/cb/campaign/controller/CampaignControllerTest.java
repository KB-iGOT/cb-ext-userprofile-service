package com.igot.cb.campaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.campaign.service.CampaignService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CampaignControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CampaignService campaignService;

    @InjectMocks
    private CampaignController campaignController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(campaignController).build();
    }

    @Test
    void testRegisterCampaignLead_Success_CampaignV1Register() throws Exception {
        Map<String, Object> dataInner = new HashMap<>();
        dataInner.put("name", "bharath");
        dataInner.put("email", "bharath@gmail.com");
        dataInner.put("phone", "8123445684");
        dataInner.put("consent", true);
        dataInner.put("consentVersion", 1);
        dataInner.put("campaignId", "Seva Bhav");
        dataInner.put("source", "non-logged-in");

        Map<String, Object> payload = Map.of("request", Map.of("data", dataInner));

        ApiResponse successResp = ProjectUtil.createDefaultResponse(Constants.API_CAMPAIGN_REGISTER);
        successResp.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
        when(campaignService.createLead(anyMap())).thenReturn(successResp);

        mockMvc.perform(post("/campaign/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.status").value(Constants.SUCCESS))
                .andExpect(jsonPath("$.result.response").value(Constants.SUCCESS));

        verify(campaignService).createLead(anyMap());
    }

    @Test
    void testRegisterCampaignLead_BadRequest_InvalidPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", "Invalid Campaign");

        ApiResponse errorResp = ProjectUtil.createDefaultResponse(Constants.API_CAMPAIGN_REGISTER);
        ProjectUtil.errorResponse(errorResp, "Invalid campaignid. Only 'Seva Bhav' is accepted", HttpStatus.BAD_REQUEST);
        when(campaignService.createLead(anyMap())).thenReturn(errorResp);

        mockMvc.perform(post("/campaign/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.status").value(Constants.FAILED))
                .andExpect(jsonPath("$.params.errMsg").value("Invalid campaignid. Only 'Seva Bhav' is accepted"));

        verify(campaignService).createLead(anyMap());
    }
}
