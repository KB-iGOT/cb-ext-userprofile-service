package com.igot.cb.profile;

import org.igot.common.auth.AccessTokenValidator;
import com.igot.cb.common.OutboundRequestHandlerServiceImpl;
import com.igot.cb.profile.service.PasswordResetServiceImpl;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    @Mock
    private CbServerProperties cbServerProperties;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private static final String AUTH_TOKEN = "valid-token";
    private static final String USER_ID = "user-123";

    @Test
    void resetPassword_shouldReturnBadRequest_whenAuthTokenIsBlank() {
        ApiResponse response = passwordResetService.resetPassword("");

        assertNotNull(response);
        assertEquals(Constants.INVALID_AUTH_TOKEN, response.getParams().getErrMsg());

        verifyNoInteractions(accessTokenValidator, outboundRequestHandlerService, cbServerProperties);
    }

    @Test
    void resetPassword_shouldReturnBadRequest_whenUserIdIsInvalid() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN))
                .thenReturn("");

        ApiResponse response = passwordResetService.resetPassword(AUTH_TOKEN);

        assertNotNull(response);
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());

        verify(accessTokenValidator).fetchUserIdFromAccessToken(AUTH_TOKEN);
        verifyNoInteractions(outboundRequestHandlerService, cbServerProperties);
    }

    @Test
    void resetPassword_shouldReturnSuccess_whenResponseIsValid() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN))
                .thenReturn(USER_ID);

        when(cbServerProperties.getLearnerServiceHost())
                .thenReturn("http://localhost");
        when(cbServerProperties.getPasswordResetPath())
                .thenReturn("/reset");

        when(outboundRequestHandlerService.fetchResultUsingPost(
                anyString(), anyMap(), anyMap()
        )).thenReturn(Map.of(Constants.RESULT, Map.of("status", "SUCCESS")));

        ApiResponse response = passwordResetService.resetPassword(AUTH_TOKEN);

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.RESULT));
    }

    @Test
    void resetPassword_shouldFail_whenDownstreamReturnsNull() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN))
                .thenReturn(USER_ID);

        when(cbServerProperties.getLearnerServiceHost())
                .thenReturn("http://localhost");
        when(cbServerProperties.getPasswordResetPath())
                .thenReturn("/reset");

        when(outboundRequestHandlerService.fetchResultUsingPost(
                anyString(), anyMap(), anyMap()
        )).thenReturn(null);

        ApiResponse response = passwordResetService.resetPassword(AUTH_TOKEN);

        assertNotNull(response);
        assertEquals(Constants.PASSWORD_RESET_FAILED, response.getParams().getErrMsg());
    }

    @Test
    void resetPassword_shouldFail_whenDownstreamReturnsNoResultKey() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN))
                .thenReturn(USER_ID);

        when(cbServerProperties.getLearnerServiceHost())
                .thenReturn("http://localhost");
        when(cbServerProperties.getPasswordResetPath())
                .thenReturn("/reset");

        when(outboundRequestHandlerService.fetchResultUsingPost(
                anyString(), anyMap(), anyMap()
        )).thenReturn(Map.of());

        ApiResponse response = passwordResetService.resetPassword(AUTH_TOKEN);

        assertNotNull(response);
        assertEquals(Constants.PASSWORD_RESET_FAILED, response.getParams().getErrMsg());
    }

    @Test
    void resetPassword_shouldFail_whenExceptionIsThrown() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN))
                .thenReturn(USER_ID);

        when(cbServerProperties.getLearnerServiceHost())
                .thenReturn("http://localhost");
        when(cbServerProperties.getPasswordResetPath())
                .thenReturn("/reset");

        when(outboundRequestHandlerService.fetchResultUsingPost(
                anyString(), anyMap(), anyMap()
        )).thenThrow(new RuntimeException("Service down"));

        ApiResponse response = passwordResetService.resetPassword(AUTH_TOKEN);

        assertNotNull(response);
        assertEquals(Constants.PASSWORD_RESET_FAILED, response.getParams().getErrMsg());
    }
}
