package com.igot.cb.profile.service;

import org.igot.common.ApiResponse;

public interface UserCompetencyService {
    ApiResponse listCompetencies(String userId, String userToken);
}
