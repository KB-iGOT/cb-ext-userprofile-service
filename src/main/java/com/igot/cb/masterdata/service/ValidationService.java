package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ValidationService {

    @Autowired
    private CbServerProperties cbServerProperties;

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_LENGTH = 50;

    public boolean validateSearchRequest(ApiResponse apiResponse, Map<String, Object> requestBody) {

        try {
            if (MapUtils.isEmpty(requestBody)) {
                ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
                return false;
            }
            Map<String, Object> searchRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            if (MapUtils.isEmpty(searchRequest)) {
                ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
                return false;
            }
            Object pageObj = searchRequest.get(Constants.PAGE_NUMBER);
            Object sizeObj = searchRequest.get(Constants.PAGE_SIZE);
            if (ObjectUtils.isNotEmpty(pageObj) && ObjectUtils.isNotEmpty(sizeObj)) {
                if (!(pageObj instanceof Number) || !(sizeObj instanceof Number)) {
                    ProjectUtil.errorResponse(apiResponse, "Pagination parameters must be numeric", HttpStatus.BAD_REQUEST);
                    return false;
                }
                int pageNumber = ((Number) pageObj).intValue();
                int pageSize = ((Number) sizeObj).intValue();
                if (pageNumber < 0 || pageSize <= 0) {
                    ProjectUtil.errorResponse(apiResponse, "Invalid pagination parameters", HttpStatus.BAD_REQUEST);
                    return false;
                }
            }
            String keyword = searchRequest.get(Constants.SEARCH_STRING) != null
                    ? searchRequest.get(Constants.SEARCH_STRING).toString()
                    : null;

            return !StringUtils.isNotBlank(keyword) || validateSearchString(keyword, apiResponse);

        } catch (Exception ex) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request parameters", HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    public boolean validateSearchString(String keyword, ApiResponse apiResponse) {

        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        keyword = keyword.trim();
        // Length validation
        if (keyword.length() < MIN_SEARCH_LENGTH) {
            ProjectUtil.errorResponse(apiResponse,
                    "searchString is too short, Minimum " + MIN_SEARCH_LENGTH + " characters are required.",
                    HttpStatus.BAD_REQUEST);
            return false;
        }
        if (keyword.length() > MAX_SEARCH_LENGTH) {
            ProjectUtil.errorResponse(apiResponse,
                    "searchString is too long, Maximum " + MAX_SEARCH_LENGTH + " characters allowed.",
                    HttpStatus.BAD_REQUEST);
            return false;
        }
        //Reject invalid characters such as "??", "@#", etc.
        if (!keyword.matches(cbServerProperties.getMasterDataSearchStringRegex())) {
            ProjectUtil.errorResponse(apiResponse,
                    "searchString contains invalid characters.",
                    HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    public boolean validateUpdateStatusRequest(ApiResponse apiResponse, Map<String, Object> requestBody) {

        if (MapUtils.isEmpty(requestBody)) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
            return false;
        }
        Map<String, Object> statusUpdateRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
        if (MapUtils.isEmpty(statusUpdateRequest)) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
            return false;
        }
        Object nameObj = statusUpdateRequest.get(Constants.NAME);
        String name = (nameObj != null) ? nameObj.toString().trim() : "";

        if (StringUtils.isBlank(name)) {
            ProjectUtil.errorResponse(apiResponse, "Institute name cannot be empty", HttpStatus.BAD_REQUEST);
            return false;
        }
        Object statusObj = statusUpdateRequest.get(Constants.STATUS);
        if (statusObj == null) {
            ProjectUtil.errorResponse(apiResponse, "Status is required", HttpStatus.BAD_REQUEST);
            return false;
        }
        int status;
        try {
            status = (statusObj instanceof Number n) ? n.intValue() : Integer.parseInt(statusObj.toString());
        } catch (NumberFormatException ex) {
            ProjectUtil.errorResponse(apiResponse, "Status must be a valid number", HttpStatus.BAD_REQUEST);
            return false;
        }
        if (status != 0 && status != 1) {
            ProjectUtil.errorResponse(apiResponse, "Status must be 0 or 1", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    public boolean addDegreeValidation(ApiResponse apiResponse, Map<String, Object> requestBody) {

        // -------- Validate requestBody --------
        if (MapUtils.isEmpty(requestBody)) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
            return false;
        }
        Object reqObj = requestBody.get(Constants.REQUEST);
        if (!(reqObj instanceof Map) || MapUtils.isEmpty((Map<?, ?>) reqObj)) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
            return false;
        }
        Map<String, Object> addDegreeRequest = (Map<String, Object>) reqObj;
        // -------- Validate degree name --------
        Object nameObj = addDegreeRequest.get(Constants.NAME);
        String name = nameObj == null ? "" : String.valueOf(nameObj).trim();

        if (StringUtils.isEmpty(name)) {
            ProjectUtil.errorResponse(apiResponse, "Degree name cannot be empty", HttpStatus.BAD_REQUEST);
            return false;
        }
        // -------- Validate degree description (optional) --------
        Object descObj = addDegreeRequest.get(Constants.DESCRIPTION);
        if (ObjectUtils.isNotEmpty(descObj)) {
            String description = String.valueOf(descObj);
            if (description.length() > 255) {
                ProjectUtil.errorResponse(apiResponse, "Degree description cannot exceed 255 characters", HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

    public boolean addInstituteValidation(ApiResponse apiResponse, Map<String, Object> requestBody) {

        // -------- Validate requestBody --------
        if (MapUtils.isEmpty(requestBody)) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
            return false;
        }
        Object reqObj = requestBody.get(Constants.REQUEST);
        if (!(reqObj instanceof Map) || MapUtils.isEmpty((Map<?, ?>) reqObj)) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request", HttpStatus.BAD_REQUEST);
            return false;
        }
        Map<String, Object> addInstituteRequest = (Map<String, Object>) reqObj;
        // -------- Validate institute name --------
        Object nameObj = addInstituteRequest.get(Constants.NAME);
        String name = nameObj == null ? "" : String.valueOf(nameObj).trim();
        if (StringUtils.isEmpty(name)) {
            ProjectUtil.errorResponse(apiResponse, "Institute name cannot be empty", HttpStatus.BAD_REQUEST);
            return false;
        }
        // -------- Validate institute description (optional) --------
        Object descObj = addInstituteRequest.get(Constants.DESCRIPTION);
        if (ObjectUtils.isNotEmpty(descObj)) {
            String description = String.valueOf(descObj);
            if (description.length() > 255) {
                ProjectUtil.errorResponse(apiResponse, "Institute description cannot exceed 255 characters", HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

}
