package com.igot.cb.util;

import static org.junit.Assert.assertEquals;
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