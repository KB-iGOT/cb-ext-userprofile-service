package com.igot.cb.user.service.impl;

import com.igot.cb.common.CbExtServerProperties;
import com.igot.cb.common.OutboundRequestHandlerServiceImpl;
import com.igot.cb.user.service.UserUtilityService;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@Slf4j
public class UserUtilityServiceImpl implements UserUtilityService {

    @Autowired
    OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    @Autowired
    CbExtServerProperties serverConfig;

    @Override
    public Map<String, Object> getUsersReadData(String userId, String authToken, String userAuthToken) {
        Map<String, String> header = new HashMap<>();
        if (StringUtils.isNotEmpty(authToken)) {
            header.put(Constants.AUTH_TOKEN, authToken);
        }
        if (StringUtils.isNotEmpty(userAuthToken)) {
            header.put(Constants.X_AUTH_TOKEN, userAuthToken);
        }
        Map<String, Object> readData = (Map<String, Object>) outboundRequestHandlerService
                .fetchUsingGetWithHeadersProfile(serverConfig.getSbUrl() + serverConfig.getLmsUserReadPath() + userId,
                        header);
        Map<String, Object> result = (Map<String, Object>) readData.get(Constants.RESULT);
        Map<String, Object> responseMap = (Map<String, Object>) result.get(Constants.RESPONSE);
        return responseMap;
    }
}
