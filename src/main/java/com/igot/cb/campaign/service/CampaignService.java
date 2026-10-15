package com.igot.cb.campaign.service;

import com.igot.cb.util.ApiResponse;

import java.util.Map;

public interface CampaignService {

    /**
     * Creates or updates a campaign lead record in Cassandra.
     *
     * @param requestBody the request body containing lead details
     * @return ApiResponse indicating the result of the operation
     */
    ApiResponse createLead(Map<String, Object> requestBody);

    /**
     * Validates the campaign lead request body and populates error details in apiResponse if invalid.
     *
     * @param apiResponse the response object to update with error details
     * @param requestBody the incoming request body
     * @return true if valid, false otherwise
     */
    boolean validation(ApiResponse apiResponse, Map<String, Object> requestBody);
}
