package com.igot.cb.profile.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;


import java.sql.Timestamp;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertAll;

public class CustomFieldEntityTest {

    @Test
    public void testCustomFieldEntity() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp later = new Timestamp(now.getTime() + 1000);

        ObjectNode jsonNode = new ObjectMapper().createObjectNode().put("key", "value");

        // Using all-args constructor
        CustomFieldEntity entity = new CustomFieldEntity("id123", jsonNode, true, true, now, now);

        // Verify initial values
        assertAll("Initial values",
                () -> assertEquals("id123", entity.getCustomFiledId()),
                () -> assertEquals(jsonNode, entity.getCustomFieldData()),
                () -> assertTrue(entity.getIsMandatory()),
                () -> assertTrue(entity.getIsActive()),
                () -> assertEquals(now, entity.getCreatedOn()),
                () -> assertEquals(now, entity.getUpdatedOn())
        );

        // Modify fields
        entity.setCustomFiledId("id456");
        entity.setIsMandatory(false);
        entity.setIsActive(false);
        entity.setCreatedOn(later);
        entity.setUpdatedOn(later);

        // Verify updated values
        assertAll("Updated values",
                () -> assertEquals("id456", entity.getCustomFiledId()),
                () -> assertFalse(entity.getIsMandatory()),
                () -> assertFalse(entity.getIsActive()),
                () -> assertEquals(later, entity.getCreatedOn()),
                () -> assertEquals(later, entity.getUpdatedOn())
        );
    }



}
