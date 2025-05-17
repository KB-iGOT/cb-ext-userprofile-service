package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;

/**
 * Author: mahesh.vakkund
 */
public interface MasterDataService {
    /**
     * Retrieves a list of all institutions from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of institutions if successful,
     *         or an error response if the operation fails
     */
    ApiResponse getInstitutionsList(String authToken);

    /**
     * Retrieves a list of all degrees from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of degrees if successful,
     *         or an error response if the operation fails
     */
    ApiResponse getDegreesList(String authToken);

}