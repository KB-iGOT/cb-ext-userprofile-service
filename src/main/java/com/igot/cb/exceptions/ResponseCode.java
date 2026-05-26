package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Manzarul
 */
@Getter
public enum ResponseCode {
    UNAUTHORIZED(Constants.UNAUTHORIZED_USER_CODE, Constants.UNAUTHORIZED_USER_MSG),
    INTERNAL_SERVER_ERROR(Constants.INTERNAL_ERROR_CODE, Constants.INTERNAL_ERROR_MSG),
    RESOURCE_NOT_FOUND(
            Constants.RESOURCE_NOT_FOUND_CODE, Constants.RESOURCE_NOT_FOUND_MSG),
    INVALID_PARAMETER_VALUE(
            Constants.INVALID_PARAMETER_VALUE_CODE, Constants.INVALID_PARAMETER_VALUE_MSG),

    OK(200),
    CLIENT_ERROR(400),
    SERVER_ERROR(500);
    @Setter
    private int httpStatusCode;
    /**
     * error code contains String value
     */
    private String errorCode;
    /**
     * errorMessage contains proper error message.
     */
    private String errorMessage;

    /**
     * @param errorCode    String
     * @param errorMessage String
     */
    ResponseCode(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    ResponseCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * This method will provide ResponseCode enum based on error code
     */
    public static ResponseCode getResponse(String errorCode) {
        if (StringUtils.isBlank(errorCode)) {
            return null;
        } else if (Constants.UNAUTHORIZED.equals(errorCode)) {
            return ResponseCode.UNAUTHORIZED;
        } else {
            ResponseCode value = null;
            ResponseCode[] responseCodes = ResponseCode.values();
            for (ResponseCode response : responseCodes) {
                if (response.getErrorCode() != null && response.getErrorCode().equals(errorCode)) {
                    return response;
                }
            }
            return value;
        }
    }
}