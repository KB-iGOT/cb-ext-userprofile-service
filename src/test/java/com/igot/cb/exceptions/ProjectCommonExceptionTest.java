package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProjectCommonExceptionTest {

    @Test
    public void testConstructorWithResponseCode() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_001");
        
        ProjectCommonException exception = new ProjectCommonException(mockCode, "Test message", 400);
        
        assertEquals("ERR_001", exception.getErrorCode());
        assertEquals("Test message", exception.getErrorMessage());
        assertEquals(400, exception.getErrorResponseCode());
        assertEquals(mockCode, exception.getResponseCode());
    }

    @Test
    public void testConstructorWithPlaceholders() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_002");
        
        ProjectCommonException exception = new ProjectCommonException(
            mockCode, 
            "Error occurred for user {0} in operation {1}", 
            500, 
            "testUser", 
            "testOperation"
        );
        
        assertEquals("ERR_002", exception.getErrorCode());
        assertEquals("Error occurred for user testUser in operation testOperation", exception.getErrorMessage());
        assertEquals(500, exception.getErrorResponseCode());
    }

    @Test
    public void testCopyConstructor() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_003");
        
        ProjectCommonException original = new ProjectCommonException(mockCode, "Original message", 400);
        ProjectCommonException copy = new ProjectCommonException(original, "NewOperation");
        
        assertEquals(Constants.USER_ORG_SERVICE_PREFIX + "NewOperation" + "ERR_003", copy.getErrorCode());
        assertEquals("Original message", copy.getErrorMessage());
        assertEquals(400, copy.getErrorResponseCode());
    }

    @Test
    public void testGettersAndSetters() {
        ProjectCommonException exception = new ProjectCommonException(
            mock(ResponseCode.class), 
            "Test message", 
            400
        );
        
        exception.setErrorCode("NEW_ERR");
        exception.setErrorMessage("New message");
        exception.setErrorResponseCode(500);
        
        assertEquals("NEW_ERR", exception.getErrorCode());
        assertEquals("New message", exception.getErrorMessage());
        assertEquals(500, exception.getErrorResponseCode());
    }

    @Test
    public void testToString() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_004");
        
        ProjectCommonException exception = new ProjectCommonException(mockCode, "Test message", 400);
        assertEquals("ERR_004: Test message", exception.toString());
    }

    @Test
    public void testThrowServerErrorException() {
        try {
            ResponseCode mockResponseCode = mock(ResponseCode.class);
            when(mockResponseCode.getErrorCode()).thenReturn("SERVER_ERR_001");
            when(mockResponseCode.getErrorMessage()).thenReturn("Default error message");
            ProjectCommonException.throwServerErrorException(mockResponseCode, "Custom error message");
            fail("Expected ProjectCommonException to be thrown");
        } catch (ProjectCommonException e) {
            assertNotNull(e.getErrorCode());
            assertEquals("Custom error message", e.getErrorMessage());
            assertNotEquals(0, e.getErrorResponseCode());
        }
        try {
            ResponseCode mockResponseCode = mock(ResponseCode.class);
            when(mockResponseCode.getErrorCode()).thenReturn("SERVER_ERR_001");
            when(mockResponseCode.getErrorMessage()).thenReturn("Default error message");

            ProjectCommonException.throwServerErrorException(mockResponseCode, "");
            fail("Expected ProjectCommonException to be thrown");
        } catch (ProjectCommonException e) {
            assertNotNull(e.getErrorCode());
            assertEquals("Default error message", e.getErrorMessage());
            assertNotEquals(0, e.getErrorResponseCode());
        }
        try {
            ResponseCode mockResponseCode = mock(ResponseCode.class);
            when(mockResponseCode.getErrorCode()).thenReturn("SERVER_ERR_001");
            when(mockResponseCode.getErrorMessage()).thenReturn("Default error message");

            ProjectCommonException.throwServerErrorException(mockResponseCode, null);
            fail("Expected ProjectCommonException to be thrown");
        } catch (ProjectCommonException e) {
            assertNotNull(e.getErrorCode());
            assertEquals("Default error message", e.getErrorMessage());
            assertNotEquals(0, e.getErrorResponseCode());
        }
    }

    @Test
    public void testThrowServerErrorExceptionWithSingleParameter() {
        try {
            ResponseCode mockResponseCode = mock(ResponseCode.class);
            when(mockResponseCode.getErrorCode()).thenReturn("SERVER_ERR_001");
            when(mockResponseCode.getErrorMessage()).thenReturn("Default error message");

            ProjectCommonException.throwServerErrorException(mockResponseCode);
            fail("Expected ProjectCommonException to be thrown");
        } catch (ProjectCommonException e) {
            assertNotNull(e.getErrorCode());
            assertEquals("Default error message", e.getErrorMessage());
            assertNotEquals(0, e.getErrorResponseCode());
        }
    }

    @Test
    public void testSetMessage() {
        ProjectCommonException exception = new ProjectCommonException(
                ResponseCode.SERVER_ERROR,
                "Initial message",
                ResponseCode.SERVER_ERROR.getResponseCode()
        );
        String newMessage = "New error message";
        exception.setMessage(newMessage);
        assertEquals(newMessage, exception.getMessage());
        exception.setMessage(null);
        assertNull(exception.getMessage());
        exception.setMessage("");
        assertEquals("", exception.getMessage());
    }

    @Test
    public void testSetResponseCode() {
        ProjectCommonException exception = new ProjectCommonException(
                ResponseCode.SERVER_ERROR,
                "Test message",
                ResponseCode.SERVER_ERROR.getResponseCode()
        );
        ResponseCode mockResponseCode = mock(ResponseCode.class);
        when(mockResponseCode.getErrorCode()).thenReturn("NEW_ERR_001");
        exception.setResponseCode(mockResponseCode);
        assertEquals(mockResponseCode, exception.getResponseCode());
        exception.setResponseCode(null);
        assertNull(exception.getResponseCode());
    }
}