package com.igot.cb.util;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConstantsTest {

    @Test
    public void testAllConstantsAreNotNull() throws IllegalAccessException {
        Field[] fields = Constants.class.getDeclaredFields();

        for (Field field : fields) {
            if (Modifier.isPublic(field.getModifiers()) &&
                    Modifier.isStatic(field.getModifiers()) &&
                    Modifier.isFinal(field.getModifiers()) &&
            field.getType().equals(String.class)) {

                Object value = field.get(null);
                assertNotNull(value, "Constant " + field.getName() + " should not be null");
            }
        }
    }

    @Test
    public void testSpecificConstantsValues() {
        assertEquals("sunbird", Constants.KEYSPACE_SUNBIRD);
        assertEquals(".fetchResult:", Constants.FETCH_RESULT_CONSTANT);
        assertEquals("URI: ", Constants.URI_CONSTANT);
        assertEquals("Authorization", Constants.AUTH_TOKEN);
    }

}
