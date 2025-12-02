package com.igot.cb.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;


import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.exceptions.ResponseCode;

@RunWith(MockitoJUnitRunner.class)
public class ProjectUtilTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProjectUtil projectUtil;

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

    @Test
    public void testParseListOfMap() throws IOException {
        String json = "[{\"key1\":\"value1\"}, {\"key2\":\"value2\"}]";

        List<Map<String, Object>> mockList = List.of(
                Map.of("key1", "value1"),
                Map.of("key2", "value2"));

        when(objectMapper.readValue(json, projectUtil.LIST_OF_MAP_TYPE)).thenReturn(mockList);

        List<Map<String, Object>> result = projectUtil.parseListOfMap(json);

        assertEquals(2, result.size());
        assertEquals("value1", result.get(0).get("key1"));
    }

    @Test
    public void testParseMap() throws IOException {
        String json = "{\"key\":\"value\"}";

        Map<String, Object> mockMap = Map.of("key", "value");

        when(objectMapper.readValue(json, projectUtil.MAP_TYPE)).thenReturn(mockMap);

        Map<String, Object> result = projectUtil.parseMap(json);

        assertEquals(1, result.size());
        assertEquals("value", result.get("key"));
    }
}