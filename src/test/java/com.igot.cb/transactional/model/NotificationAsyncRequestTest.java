package com.igot.cb.transactional.model;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NotificationAsyncRequestTest {

    @Test
    void testGettersAndSetters() {
        NotificationAsyncRequest request = new NotificationAsyncRequest();
        String type = "email";
        int priority = 1;
        Map<String, Object> action = new HashMap<>();
        action.put("template", "welcome");
        action.put("subject", "Welcome to our service");
        List<String> ids = Arrays.asList("user1", "user2");
        List<String> copyEmail = Arrays.asList("admin@example.com", "support@example.com");
        request.setType(type);
        request.setPriority(priority);
        request.setAction(action);
        request.setIds(ids);
        request.setCopyEmail(copyEmail);
        assertEquals(type, request.getType());
        assertEquals(priority, request.getPriority());
        assertEquals(action, request.getAction());
        assertEquals(ids, request.getIds());
        assertEquals(copyEmail, request.getCopyEmail());
    }

    @Test
    void testDefaultValues() {
        NotificationAsyncRequest request = new NotificationAsyncRequest();
        assertNull(request.getType());
        assertEquals(0, request.getPriority()); // default for int is 0
        assertNull(request.getAction());
        assertNull(request.getIds());
        assertNull(request.getCopyEmail());
    }

    @Test
    void testComplexActionMap() {
        NotificationAsyncRequest request = new NotificationAsyncRequest();
        Map<String, Object> action = new HashMap<>();
        Map<String, Object> template = new HashMap<>();
        template.put("id", "welcome-template");
        template.put("version", 2);
        Map<String, Object> params = new HashMap<>();
        params.put("username", "JohnDoe");
        params.put("activationLink", "https://example.com/activate");
        template.put("params", params);
        action.put("template", template);
        action.put("channel", "email");
        request.setAction(action);
        assertEquals(action, request.getAction());
        Map<String, Object> retrievedAction = request.getAction();
        Map<String, Object> retrievedTemplate = (Map<String, Object>) retrievedAction.get("template");
        assertEquals("welcome-template", retrievedTemplate.get("id"));
        assertEquals(2, retrievedTemplate.get("version"));
        Map<String, Object> retrievedParams = (Map<String, Object>) retrievedTemplate.get("params");
        assertEquals("JohnDoe", retrievedParams.get("username"));
        assertEquals("https://example.com/activate", retrievedParams.get("activationLink"));
    }
}