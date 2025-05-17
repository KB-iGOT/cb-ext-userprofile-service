package com.igot.cb.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.igot.cb.exceptions.CustomException;
import com.igot.cb.exceptions.ResponseCode;

@RunWith(MockitoJUnitRunner.class)
public class ProjectUtilTest {

    @Test
    public void testCreateServerError() {
        ResponseCode responseCode = ResponseCode.SERVER_ERROR;
        CustomException exception = ProjectUtil.createServerError(responseCode);
        assertNotNull(exception);
        assertEquals(responseCode.getErrorCode(), exception.getErrorCode());
        assertEquals(responseCode.getErrorMessage(), exception.getMessage());
        assertEquals(Integer.valueOf(ResponseCode.SERVER_ERROR.getResponseCode()),
                Integer.valueOf(exception.getResponseCode()));
    }

    @Test
    public void testCreateClientException() {
        ResponseCode responseCode = ResponseCode.CLIENT_ERROR;
        CustomException exception = ProjectUtil.createClientException(responseCode);
        assertNotNull(exception);
        assertEquals(responseCode.getErrorCode(), exception.getErrorCode());
        assertEquals(responseCode.getErrorMessage(), exception.getMessage());
        assertEquals(Integer.valueOf(ResponseCode.CLIENT_ERROR.getResponseCode()),
                Integer.valueOf(exception.getResponseCode()));
    }
}