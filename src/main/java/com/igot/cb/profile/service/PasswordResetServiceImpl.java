package com.igot.cb.profile.service;

import org.igot.common.auth.AccessTokenValidator;
import com.igot.cb.common.OutboundRequestHandlerServiceImpl;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {


    private final AccessTokenValidator accessTokenValidator;
    private final OutboundRequestHandlerServiceImpl outboundRequestHandlerService;
    private final CbServerProperties cbServerProperties;

    public PasswordResetServiceImpl(AccessTokenValidator accessTokenValidator, OutboundRequestHandlerServiceImpl outboundRequestHandlerService, CbServerProperties cbServerProperties) {
        this.accessTokenValidator = accessTokenValidator;
        this.outboundRequestHandlerService = outboundRequestHandlerService;
        this.cbServerProperties = cbServerProperties;
    }

    @Override
    public ApiResponse resetPassword(String authToken) {

        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_USER_PASSWORD_RESET_V2);
        if (StringUtils.isBlank(authToken)) {
            ProjectUtil.errorResponse(apiResponse, Constants.INVALID_AUTH_TOKEN, HttpStatus.BAD_REQUEST);
            return apiResponse;
        }
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            ProjectUtil.errorResponse(apiResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return apiResponse;
        }

        Map<String, String> headers = Map.of(
                Constants.CONTENT_TYPE, Constants.APPLICATION_JSON
        );

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(
                        Constants.USER_ID, userId,
                        Constants.KEY, Constants.USER,
                        Constants.TYPE, Constants.EMAIL
                )
        );
        try {
            String url = cbServerProperties.getLearnerServiceHost() + cbServerProperties.getPasswordResetPath();
            Map<String, Object> response = outboundRequestHandlerService.fetchResultUsingPost(url, requestBody, headers);
            if (response == null || !response.containsKey(Constants.RESULT)) {
                ProjectUtil.errorResponse(apiResponse, Constants.PASSWORD_RESET_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
                return apiResponse;
            }
            apiResponse.getResult().put(Constants.RESULT, response.get(Constants.RESULT));
            return apiResponse;
        } catch (Exception ex) {
            log.error("Password reset failed for userId={}", userId, ex);
            ProjectUtil.errorResponse(apiResponse, Constants.PASSWORD_RESET_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return apiResponse;
    }
}

