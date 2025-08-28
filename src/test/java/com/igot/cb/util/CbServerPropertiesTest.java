package com.igot.cb.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CbServerPropertiesTest {

    private CbServerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CbServerProperties();
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = CbServerProperties.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(properties, value);
    }

    @Test
    void testGettersAndSetters() throws Exception {
        setField("basicProfileFields", "name,email,phone");
        setField("profileCompletionRequiredFields", "field1,field2");

        // Test getBasicProfileFields
        List<String> basicFields = properties.getBasicProfileFields();
        assertEquals(3, basicFields.size());
        assertEquals("name", basicFields.get(0));

        // Test getProfileCompletionRequiredFields
        List<String> requiredFields = properties.getProfileCompletionRequiredFields();
        assertEquals(2, requiredFields.size());
        assertEquals("field1", requiredFields.get(0));

        // Test simple getter and setter
        properties.setRedisInsightIndex(5);
        assertEquals(5, properties.getRedisInsightIndex());

        properties.setSearchResultRedisTtl(100L);
        assertEquals(100L, properties.getSearchResultRedisTtl());

        properties.setSbApiKey("api-key");
        assertEquals("api-key", properties.getSbApiKey());

        properties.setRequestTimeoutMs(3000);
        assertEquals(3000, properties.getRequestTimeoutMs());

        properties.setContextType(new String[]{"A", "B"});
        assertArrayEquals(new String[]{"A", "B"}, properties.getContextType());

        properties.setExtendedFieldsConfig(List.of("ext1", "ext2"));
        assertEquals(2, properties.getExtendedFieldsConfig().size());

        properties.setRedisTestOnBorrow(Boolean.TRUE);
        assertTrue(properties.getRedisTestOnBorrow());

        properties.setRedisTimeBetweenEvictionRunsMillis(12345L);
        assertEquals(12345L, properties.getRedisTimeBetweenEvictionRunsMillis());

        properties.setCertificateCountRedisKey("certKey");
        assertEquals("certKey", properties.getCertificateCountRedisKey());

        properties.setConnectionApi("http://api");
        assertEquals("http://api", properties.getConnectionApi());
    }
}

