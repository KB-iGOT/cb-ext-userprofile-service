package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.PasswordResetService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PasswordResetController controller =
                new PasswordResetController(passwordResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void resetPassword_shouldReturnOk_whenServiceReturnsSuccess() throws Exception {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);

        when(passwordResetService.resetPassword(anyString()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/user/v2/reset/password")
                        .header(Constants.X_AUTH_TOKEN, "valid-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(passwordResetService, times(1))
                .resetPassword("valid-token");
    }

    @Test
    void resetPassword_shouldReturnBadRequest_whenServiceReturnsBadRequest() throws Exception {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(passwordResetService.resetPassword(anyString()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/user/v2/reset/password")
                        .header(Constants.X_AUTH_TOKEN, "invalid-token"))
                .andExpect(status().isBadRequest());

        verify(passwordResetService).resetPassword("invalid-token");
    }

    @Test
    void resetPassword_shouldReturnInternalServerError_whenServiceFails() throws Exception {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);

        when(passwordResetService.resetPassword(anyString()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/user/v2/reset/password")
                        .header(Constants.X_AUTH_TOKEN, "token"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void resetPassword_shouldFail_whenAuthTokenHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/user/v2/reset/password"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }
}