package com.igot.cb.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiRespParamTest {

    @Test
    void testDefaultConstructor() {
        ApiRespParam params = new ApiRespParam();
        assertNull(params.getResmsgid(), "resmsgid should be null with default constructor");
        assertNull(params.getMsgid(), "msgid should be null with default constructor");
        assertNull(params.getErr(), "err should be null with default constructor");
        assertNull(params.getStatus(), "status should be null with default constructor");
        assertNull(params.getErrmsg(), "errmsg should be null with default constructor");
    }

    @Test
    void testParameterizedConstructor() {
        String id = "test-id-123";
        ApiRespParam params = new ApiRespParam(id);
        assertEquals(id, params.getResmsgid(), "resmsgid should match the provided id");
        assertEquals(id, params.getMsgid(), "msgid should match the provided id");
        assertNull(params.getErr(), "err should be null initially");
        assertNull(params.getStatus(), "status should be null initially");
        assertNull(params.getErrmsg(), "errmsg should be null initially");
    }

    @Test
    void testSettersAndGetters() {
        ApiRespParam params = new ApiRespParam();
        String resmsgid = "res-123";
        String msgid = "msg-456";
        String err = "ERR_001";
        String status = "FAILED";
        String errmsg = "An error occurred";
        params.setResmsgid(resmsgid);
        params.setMsgid(msgid);
        params.setErr(err);
        params.setStatus(status);
        params.setErrmsg(errmsg);
        assertEquals(resmsgid, params.getResmsgid(), "resmsgid should be set correctly");
        assertEquals(msgid, params.getMsgid(), "msgid should be set correctly");
        assertEquals(err, params.getErr(), "err should be set correctly");
        assertEquals(status, params.getStatus(), "status should be set correctly");
        assertEquals(errmsg, params.getErrmsg(), "errmsg should be set correctly");
    }

    @Test
    void testWithEmptyStringValues() {
        ApiRespParam params = new ApiRespParam();
        String emptyString = "";
        params.setResmsgid(emptyString);
        params.setMsgid(emptyString);
        params.setErr(emptyString);
        params.setStatus(emptyString);
        params.setErrmsg(emptyString);
        assertEquals(emptyString, params.getResmsgid(), "resmsgid should be set to empty string");
        assertEquals(emptyString, params.getMsgid(), "msgid should be set to empty string");
        assertEquals(emptyString, params.getErr(), "err should be set to empty string");
        assertEquals(emptyString, params.getStatus(), "status should be set to empty string");
        assertEquals(emptyString, params.getErrmsg(), "errmsg should be set to empty string");
    }

    @Test
    void testSettingNullValues() {
        ApiRespParam params = new ApiRespParam("initial-id");
        params.setResmsgid(null);
        params.setMsgid(null);
        params.setErr(null);
        params.setStatus(null);
        params.setErrmsg(null);
        assertNull(params.getResmsgid(), "resmsgid should be set to null");
        assertNull(params.getMsgid(), "msgid should be set to null");
        assertNull(params.getErr(), "err should be set to null");
        assertNull(params.getStatus(), "status should be set to null");
        assertNull(params.getErrmsg(), "errmsg should be set to null");
    }
}