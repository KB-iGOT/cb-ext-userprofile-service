package com.igot.cb.profile.service;

import com.igot.cb.util.ApiResponse;

public interface PasswordResetService {

    ApiResponse resetPassword(String authToken);
}
