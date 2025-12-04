package com.igot.cb.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.igot.common.ApiResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(MockitoJUnitRunner.class)
public class ProjectUtilTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProjectUtil projectUtil;

    @Test
    public void testParseListOfMap() throws IOException {
        String json = "[{\"key1\":\"value1\"}, {\"key2\":\"value2\"}]";

        List<Map<String, Object>> mockList = List.of(
                Map.of("key1", "value1"),
                Map.of("key2", "value2"));

        when(objectMapper.readValue(json, projectUtil.listOfMapType)).thenReturn(mockList);

        List<Map<String, Object>> result = projectUtil.parseListOfMap(json);

        assertEquals(2, result.size());
        assertEquals("value1", result.get(0).get("key1"));
    }

    @Test
    public void testParseMap() throws IOException {
        String json = "{\"key\":\"value\"}";

        Map<String, Object> mockMap = Map.of("key", "value");

        when(objectMapper.readValue(json, projectUtil.mapType)).thenReturn(mockMap);

        Map<String, Object> result = projectUtil.parseMap(json);

        assertEquals(1, result.size());
        assertEquals("value", result.get("key"));
    }

    @Test
    public void testConvertToString_Success() throws JsonProcessingException {
        Map<String, Object> testObject = new HashMap<>();
        testObject.put("key1", "value1");
        testObject.put("key2", 123);

        String expectedJson = "{\"key1\":\"value1\",\"key2\":123}";

        when(objectMapper.writeValueAsString(testObject)).thenReturn(expectedJson);

        String result = projectUtil.convertToString(testObject);

        assertNotNull(result);
        assertEquals(expectedJson, result);
    }

    @Test
    public void testConvertToString_WithNullObject() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(null)).thenReturn("null");

        String result = projectUtil.convertToString(null);

        assertEquals("null", result);
    }

    @Test
    public void testConvertToString_WithEmptyMap() throws JsonProcessingException {
        Map<String, Object> emptyMap = new HashMap<>();
        String expectedJson = "{}";

        when(objectMapper.writeValueAsString(emptyMap)).thenReturn(expectedJson);

        String result = projectUtil.convertToString(emptyMap);

        assertEquals(expectedJson, result);
    }

    @Test
    public void testConvertToString_IOException() throws JsonProcessingException {
        Map<String, Object> testObject = new HashMap<>();
        testObject.put("key", "value");

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Test exception") {});

        String result = projectUtil.convertToString(testObject);

        assertNull(result);
    }

    @Test
    public void testBuildCacheKey() {
        String prefix = "user";
        String contextType = "profile";
        String userId = "12345";

        String result = projectUtil.buildCacheKey(prefix, contextType, userId);

        assertEquals("user:profile:12345", result);
    }

    @Test
    public void testBuildCacheKey_WithEmptyStrings() {
        String result = projectUtil.buildCacheKey("", "", "");

        assertEquals("::", result);
    }

    @Test
    public void testBuildCacheKey_WithSpecialCharacters() {
        String prefix = "user@test";
        String contextType = "profile#data";
        String userId = "user-123-abc";

        String result = projectUtil.buildCacheKey(prefix, contextType, userId);

        assertEquals("user@test:profile#data:user-123-abc", result);
    }

    @Test
    public void testErrorResponse() {
        ApiResponse response = ApiResponse.createDefaultResponse("TEST_API");
        String errorMessage = "Test error message";
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

        ProjectUtil.errorResponse(response, errorMessage, httpStatus);

        assertEquals(httpStatus, response.getResponseCode());
        assertEquals(errorMessage, response.getParams().getErrMsg());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }
}