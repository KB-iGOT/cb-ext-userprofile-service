package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;

import java.util.Map;

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

    /**
     * Updates institution information in the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @param requestBody Map containing institution details to be updated
     * @return ApiResponse containing the updated list of institutions if successful,
     *         or an error response if the operation fails
     */
    ApiResponse updateInstitutionList(String authToken, Map<String, Object> requestBody);


    /**
     * Updates degree information in the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @param requestBody Map containing degree details to be updated
     * @return ApiResponse containing the updated list of degrees if successful,
     *         or an error response if the operation fails
     */
    ApiResponse updateDegreesList(String authToken, Map<String, Object> requestBody);
}