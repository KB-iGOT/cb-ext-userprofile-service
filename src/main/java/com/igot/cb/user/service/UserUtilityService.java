package com.igot.cb.user.service;

import java.util.List;
import java.util.Map;

public interface UserUtilityService {

    Map<String, Object> getUsersReadData(String userId, String authToken, String X_authToken);
}
