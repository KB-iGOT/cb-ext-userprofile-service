package com.igot.cb.transactional.elasticsearch.dto;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for FacetDTO class
 * Tests all constructors, getters, setters, equals, hashCode, and serialization
 */
class FacetDTOTest {

    // ==================== Constructor Tests ====================

    @Test
    void testNoArgsConstructor() {
        FacetDTO facetDTO = new FacetDTO();

        assertNotNull(facetDTO);
        assertNull(facetDTO.getValue());
        assertNull(facetDTO.getCount());
    }

    @Test
    void testAllArgsConstructor() {
        String value = "category1";
        Long count = 100L;

        FacetDTO facetDTO = new FacetDTO(value, count);

        assertNotNull(facetDTO);
        assertEquals(value, facetDTO.getValue());
        assertEquals(count, facetDTO.getCount());
    }

    @Test
    void testAllArgsConstructorWithNullValues() {
        FacetDTO facetDTO = new FacetDTO(null, null);

        assertNotNull(facetDTO);
        assertNull(facetDTO.getValue());
        assertNull(facetDTO.getCount());
    }

    // ==================== Getter and Setter Tests ====================

    @Test
    void testGetValue() {
        FacetDTO facetDTO = new FacetDTO();
        String value = "status";
        facetDTO.setValue(value);

        assertEquals(value, facetDTO.getValue());
    }

    @Test
    void testSetValue() {
        FacetDTO facetDTO = new FacetDTO();
        String value = "type";

        facetDTO.setValue(value);

        assertEquals(value, facetDTO.getValue());
    }

    @Test
    void testSetValueWithNull() {
        FacetDTO facetDTO = new FacetDTO("initial", 10L);

        facetDTO.setValue(null);

        assertNull(facetDTO.getValue());
    }

    @Test
    void testSetValueOverwrite() {
        FacetDTO facetDTO = new FacetDTO();
        facetDTO.setValue("first");
        facetDTO.setValue("second");

        assertEquals("second", facetDTO.getValue());
    }

    @Test
    void testGetCount() {
        FacetDTO facetDTO = new FacetDTO();
        Long count = 50L;
        facetDTO.setCount(count);

        assertEquals(count, facetDTO.getCount());
    }

    @Test
    void testSetCount() {
        FacetDTO facetDTO = new FacetDTO();
        Long count = 200L;

        facetDTO.setCount(count);

        assertEquals(count, facetDTO.getCount());
    }

    @Test
    void testSetCountWithNull() {
        FacetDTO facetDTO = new FacetDTO("value", 100L);

        facetDTO.setCount(null);

        assertNull(facetDTO.getCount());
    }

    @Test
    void testSetCountOverwrite() {
        FacetDTO facetDTO = new FacetDTO();
        facetDTO.setCount(100L);
        facetDTO.setCount(200L);

        assertEquals(200L, facetDTO.getCount());
    }

    @Test
    void testSetCountWithZero() {
        FacetDTO facetDTO = new FacetDTO();

        facetDTO.setCount(0L);

        assertEquals(0L, facetDTO.getCount());
    }

    @Test
    void testSetCountWithNegative() {
        FacetDTO facetDTO = new FacetDTO();

        facetDTO.setCount(-1L);

        assertEquals(-1L, facetDTO.getCount());
    }

    @Test
    void testSetCountWithMaxValue() {
        FacetDTO facetDTO = new FacetDTO();

        facetDTO.setCount(Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, facetDTO.getCount());
    }

    @Test
    void testSetCountWithMinValue() {
        FacetDTO facetDTO = new FacetDTO();

        facetDTO.setCount(Long.MIN_VALUE);

        assertEquals(Long.MIN_VALUE, facetDTO.getCount());
    }

    // ==================== Chaining Tests ====================

    @Test
    void testSetterChaining() {
        FacetDTO facetDTO = new FacetDTO();

        facetDTO.setValue("test");
        facetDTO.setCount(100L);

        assertEquals("test", facetDTO.getValue());
        assertEquals(100L, facetDTO.getCount());
    }

    // ==================== Edge Case Tests ====================

    @Test
    void testEmptyStringValue() {
        FacetDTO facetDTO = new FacetDTO("", 10L);

        assertEquals("", facetDTO.getValue());
        assertEquals(10L, facetDTO.getCount());
    }

    @Test
    void testVeryLongStringValue() {
        String longValue = "a".repeat(1000);
        FacetDTO facetDTO = new FacetDTO(longValue, 5L);

        assertEquals(longValue, facetDTO.getValue());
        assertEquals(5L, facetDTO.getCount());
    }

    @Test
    void testSpecialCharactersInValue() {
        String specialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?~`";
        FacetDTO facetDTO = new FacetDTO(specialChars, 15L);

        assertEquals(specialChars, facetDTO.getValue());
    }

    @Test
    void testUnicodeCharactersInValue() {
        String unicode = "日本語🎉中文";
        FacetDTO facetDTO = new FacetDTO(unicode, 20L);

        assertEquals(unicode, facetDTO.getValue());
    }

    // ==================== Serialization Tests ====================

    @Test
    void testSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(FacetDTO.class),
                "FacetDTO should implement Serializable");
    }

    @Test
    void testSerialization() throws IOException, ClassNotFoundException {
        FacetDTO original = new FacetDTO("category", 100L);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        FacetDTO deserialized = (FacetDTO) ois.readObject();
        ois.close();

        // Verify
        assertNotNull(deserialized);
        assertEquals(original.getValue(), deserialized.getValue());
        assertEquals(original.getCount(), deserialized.getCount());
    }

    @Test
    void testSerializationWithNullValues() throws IOException, ClassNotFoundException {
        FacetDTO original = new FacetDTO(null, null);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        FacetDTO deserialized = (FacetDTO) ois.readObject();
        ois.close();

        // Verify
        assertNotNull(deserialized);
        assertNull(deserialized.getValue());
        assertNull(deserialized.getCount());
    }

    // ==================== Equals and HashCode Tests ====================

    @Test
    void testEqualsWithSameObject() {
        FacetDTO facetDTO = new FacetDTO("value", 100L);

        assertEquals(facetDTO, facetDTO);
    }

    @Test
    void testEqualsWithDifferentValue() {
        FacetDTO facetDTO1 = new FacetDTO("value1", 100L);
        FacetDTO facetDTO2 = new FacetDTO("value2", 100L);

        assertNotEquals(facetDTO1, facetDTO2);
    }

    @Test
    void testEqualsWithDifferentCount() {
        FacetDTO facetDTO1 = new FacetDTO("value", 100L);
        FacetDTO facetDTO2 = new FacetDTO("value", 200L);

        assertNotEquals(facetDTO1, facetDTO2);
    }

    @Test
    void testEqualsWithNull() {
        FacetDTO facetDTO = new FacetDTO("value", 100L);

        assertNotEquals(null, facetDTO);
    }

    @Test
    void testEqualsWithDifferentClass() {
        FacetDTO facetDTO = new FacetDTO("value", 100L);
        String differentClass = "different";

        assertNotEquals(facetDTO, differentClass);
    }

    @Test
    void testEqualsWithOneNullValue() {
        FacetDTO facetDTO1 = new FacetDTO(null, 100L);
        FacetDTO facetDTO2 = new FacetDTO("value", 100L);

        assertNotEquals(facetDTO1, facetDTO2);
    }

    @Test
    void testEqualsWithOneNullCount() {
        FacetDTO facetDTO1 = new FacetDTO("value", null);
        FacetDTO facetDTO2 = new FacetDTO("value", 100L);

        assertNotEquals(facetDTO1, facetDTO2);
    }

    @Test
    void testHashCodeConsistency() {
        FacetDTO facetDTO = new FacetDTO("value", 100L);

        int hashCode1 = facetDTO.hashCode();
        int hashCode2 = facetDTO.hashCode();

        assertEquals(hashCode1, hashCode2);
    }

    @Test
    void testHashCodeWithDifferentObjects() {
        FacetDTO facetDTO1 = new FacetDTO("value1", 100L);
        FacetDTO facetDTO2 = new FacetDTO("value2", 200L);

        // HashCode should typically be different (not guaranteed but likely)
        assertNotEquals(facetDTO1.hashCode(), facetDTO2.hashCode());
    }

    @Test
    void testToString() {
        FacetDTO facetDTO = new FacetDTO("category", 100L);

        String toString = facetDTO.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("FacetDTO") || toString.contains("category") || toString.contains("100"));
    }

    @Test
    void testToStringWithNullValues() {
        FacetDTO facetDTO = new FacetDTO(null, null);

        String toString = facetDTO.toString();

        assertNotNull(toString);
    }

    @Test
    void testToStringNotNull() {
        FacetDTO facetDTO = new FacetDTO();

        assertNotNull(facetDTO.toString());
    }

    @Test
    void testTypicalUsageScenario() {
        // Simulating typical usage in search results
        FacetDTO categoryFacet = new FacetDTO("Books", 150L);
        FacetDTO statusFacet = new FacetDTO("Active", 200L);
        FacetDTO priorityFacet = new FacetDTO("High", 75L);

        assertNotNull(categoryFacet.getValue());
        assertNotNull(categoryFacet.getCount());
        assertTrue(categoryFacet.getCount() > 0);

        assertNotEquals(categoryFacet, statusFacet);
        assertNotEquals(statusFacet, priorityFacet);
    }

    @Test
    void testBuilderPatternLike() {
        FacetDTO facetDTO = new FacetDTO();

        // Simulating builder-like pattern
        facetDTO.setValue("category");
        facetDTO.setCount(100L);

        assertEquals("category", facetDTO.getValue());
        assertEquals(100L, facetDTO.getCount());
    }

    // ==================== Boundary Tests ====================

    @Test
    void testWithVeryLargeCount() {
        FacetDTO facetDTO = new FacetDTO("value", Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, facetDTO.getCount());
    }

    @Test
    void testWithVerySmallCount() {
        FacetDTO facetDTO = new FacetDTO("value", Long.MIN_VALUE);

        assertEquals(Long.MIN_VALUE, facetDTO.getCount());
    }

    @Test
    void testMultipleInstances() {
        FacetDTO facet1 = new FacetDTO("category1", 10L);
        FacetDTO facet2 = new FacetDTO("category2", 20L);
        FacetDTO facet3 = new FacetDTO("category3", 30L);

        assertNotEquals(facet1, facet2);
        assertNotEquals(facet2, facet3);
        assertNotEquals(facet1, facet3);
    }
}
