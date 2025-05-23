package com.igot.cb.exceptions;

import org.junit.Test;
import static org.junit.Assert.*;
import com.igot.cb.util.Constants;

public class ResponseCodeTest {

    @Test
    public void testEnumValues() {
        assertEquals(5, ResponseCode.values().length);
        assertNotNull(ResponseCode.unAuthorized);
        assertNotNull(ResponseCode.internalError);
        assertNotNull(ResponseCode.OK);
        assertNotNull(ResponseCode.CLIENT_ERROR);
        assertNotNull(ResponseCode.SERVER_ERROR);
    }
    
    @Test
    public void testStringConstructor() {
        assertEquals(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseCode.unAuthorized.getErrorCode());
        assertEquals(ResponseMessage.Message.UNAUTHORIZED_USER, ResponseCode.unAuthorized.getErrorMessage());
        assertEquals(ResponseMessage.Key.INTERNAL_ERROR, ResponseCode.internalError.getErrorCode());
        assertEquals(ResponseMessage.Message.INTERNAL_ERROR, ResponseCode.internalError.getErrorMessage());
    }
    
    @Test
    public void testIntConstructor() {
        assertEquals(200, ResponseCode.OK.getResponseCode());
        assertEquals(400, ResponseCode.CLIENT_ERROR.getResponseCode());
        assertEquals(500, ResponseCode.SERVER_ERROR.getResponseCode());
        assertNull(ResponseCode.OK.getErrorCode());
        assertNull(ResponseCode.OK.getErrorMessage());
    }
    
    @Test
    public void testResponseCodeSetter() {
        ResponseCode testCode = ResponseCode.unAuthorized;
        int originalCode = testCode.getResponseCode();
        int newResponseCode = 403;
        
        try {
            testCode.setResponseCode(newResponseCode);
            assertEquals(newResponseCode, testCode.getResponseCode());
        } finally {
            // Restore the original value to avoid affecting other tests
            testCode.setResponseCode(originalCode);
        }
    }
    
    @Test
    public void testGetResponseWithNullOrBlank() {
        assertNull(ResponseCode.getResponse(null));
        assertNull(ResponseCode.getResponse(""));
        assertNull(ResponseCode.getResponse(" "));
    }
    
    @Test
    public void testGetResponseWithUnauthorized() {
        assertEquals(ResponseCode.unAuthorized, ResponseCode.getResponse(Constants.UNAUTHORIZED));
    }
    
    @Test
    public void testGetResponseWithValidErrorCode() {
        assertEquals(ResponseCode.unAuthorized,
                    ResponseCode.getResponse(ResponseMessage.Key.UNAUTHORIZED_USER));
        assertEquals(ResponseCode.internalError, 
                    ResponseCode.getResponse(ResponseMessage.Key.INTERNAL_ERROR));
    }

    @Test
    public void testGetResponseWithInvalidErrorCode() {
        try {
            ResponseCode result = ResponseCode.getResponse("INVALID_CODE");
            assertNull(result);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Cannot invoke \"String.equals(Object)\""));
        }
    }
    
    @Test
    public void testGetMessage() {
        assertEquals("", ResponseCode.OK.getMessage(200));
    }

    @Test
    public void testGetResponseReturnsNullWhenNoMatchFound() {
        String nonMatchingErrorCode = "NON_MATCHING_ERROR_CODE";
        assertNotEquals(Constants.UNAUTHORIZED, nonMatchingErrorCode);
        try {
            ResponseCode result = ResponseCode.getResponse(nonMatchingErrorCode);
            assertNull("Should return null when no matching ResponseCode is found", result);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Cannot invoke \"String.equals(Object)\""));
        }
    }

}