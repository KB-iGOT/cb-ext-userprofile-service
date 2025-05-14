package com.igot.cb.extendedprofile.service;

import com.igot.cb.util.ApiResponse;

/**
 * @author mahesh.vakkund
 */
public interface ExtendedProfileService {

    /**
     * Retrieves a list of all states from the master data.
     *
     * @param authToken The authentication token used to validate the user
     * @return ApiResponse containing the list of states or an error response
     */
    ApiResponse getStatesList(String authToken);

    /**
     * Retrieves a list of districts grouped by states from the master data.
     * Each state contains a list of its districts.
     *
     * @param authToken The authentication token used to validate the user
     * @return ApiResponse containing the list of districts by state or an error response
     */
    ApiResponse getDistrictsList(String authToken);
}