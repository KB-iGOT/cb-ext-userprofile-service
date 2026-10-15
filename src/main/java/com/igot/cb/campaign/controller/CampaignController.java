package com.igot.cb.campaign.controller;

import com.igot.cb.campaign.service.CampaignService;
import com.igot.cb.util.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping("/campaign/v1/register")
    public ResponseEntity<ApiResponse> registerCampaignLead(@RequestBody Map<String, Object> request) {
        ApiResponse response = campaignService.createLead(request);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
