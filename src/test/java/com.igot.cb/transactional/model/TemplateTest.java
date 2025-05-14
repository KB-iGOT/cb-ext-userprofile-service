package com.igot.cb.transactional.model;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateTest {

    @Test
    void testConstructorAndGetters() {
        String data = "Hello, ${name}!";
        String id = "greeting-template";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        Template template = new Template(data, id, params);
        assertEquals(data, template.getData());
        assertEquals(id, template.getId());
        assertEquals(params, template.getParams());
    }
    
    @Test
    void testSetters() {
        Template template = new Template(null, null, null);
        String data = "Welcome, ${user}!";
        String id = "welcome-template";
        Map<String, Object> params = new HashMap<>();
        params.put("user", "Alice");
        template.setData(data);
        template.setId(id);
        template.setParams(params);
        assertEquals(data, template.getData());
        assertEquals(id, template.getId());
        assertEquals(params, template.getParams());
    }
    
    @Test
    void testToString() {
        String data = "Test data";
        String id = "test-id";
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", 42);
        Template template = new Template(data, id, params);
        String result = template.toString();
        assertTrue(result.contains("data='" + data + "'"));
        assertTrue(result.contains("id='" + id + "'"));
        assertTrue(result.contains("params=" + params));
    }
    
    @Test
    void testComplexParams() {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nestedKey", "nestedValue");
        Map<String, Object> params = new HashMap<>();
        params.put("string", "text");
        params.put("number", 123);
        params.put("boolean", true);
        params.put("nested", nestedMap);
        Template template = new Template("data", "id", params);
        Map<String, Object> retrievedParams = template.getParams();
        assertEquals("text", retrievedParams.get("string"));
        assertEquals(123, retrievedParams.get("number"));
        assertEquals(true, retrievedParams.get("boolean"));
        assertEquals(nestedMap, retrievedParams.get("nested"));
    }
}