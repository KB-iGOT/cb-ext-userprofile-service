package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    @Autowired
    private CbServerProperties cbServerProperties;

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_LENGTH = 100;

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

}
