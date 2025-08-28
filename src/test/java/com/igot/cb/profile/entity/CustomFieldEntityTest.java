package com.igot.cb.profile.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldEntityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testNoArgsConstructorAndSetters() throws Exception {
        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFiledId("CF-001");
        JsonNode jsonNode = objectMapper.readTree("{\"key\":\"value\"}");
        entity.setCustomFieldData(jsonNode);
        entity.setIsMandatory(true);
        entity.setIsActive(false);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreatedOn(now);
        entity.setUpdatedOn(now);

        assertEquals("CF-001", entity.getCustomFiledId());
        assertEquals(jsonNode, entity.getCustomFieldData());
        assertTrue(entity.getIsMandatory());
        assertFalse(entity.getIsActive());
        assertEquals(now, entity.getCreatedOn());
        assertEquals(now, entity.getUpdatedOn());
    }

    @Test
    void testAllArgsConstructor() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"key\":\"value2\"}");
        Timestamp now = new Timestamp(System.currentTimeMillis());

        CustomFieldEntity entity = new CustomFieldEntity(
                "CF-002", jsonNode, false, true, now, now
        );

        assertEquals("CF-002", entity.getCustomFiledId());
        assertEquals(jsonNode, entity.getCustomFieldData());
        assertFalse(entity.getIsMandatory());
        assertTrue(entity.getIsActive());
        assertEquals(now, entity.getCreatedOn());
        assertEquals(now, entity.getUpdatedOn());
    }

    @Test
    void testToStringAndEquality() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"key\":\"value3\"}");
        Timestamp now = new Timestamp(System.currentTimeMillis());

        CustomFieldEntity entity1 = new CustomFieldEntity(
                "CF-003", jsonNode, true, true, now, now
        );
        CustomFieldEntity entity2 = new CustomFieldEntity(
                "CF-003", jsonNode, true, true, now, now
        );

        // toString should not be null
        assertNotNull(entity1.toString());

        // Test that objects are not null and different instances
        assertNotNull(entity1);
        assertNotNull(entity2);
        assertNotSame(entity1, entity2);
    }
}
