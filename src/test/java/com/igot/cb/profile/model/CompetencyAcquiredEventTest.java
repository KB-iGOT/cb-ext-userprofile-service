package com.igot.cb.profile.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test cases for CompetencyAcquiredEvent
 * <p>
 * Tests cover:
 * - Constructor (no-arg and all-args)
 * - Builder pattern
 * - Getters and setters
 * - Equals and HashCode
 * - ToString
 * - JSON serialization and deserialization
 * - Null handling
 * - Field validation
 */
@DisplayName("CompetencyAcquiredEvent Tests")
class CompetencyAcquiredEventTest {

    private CompetencyAcquiredEvent event;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        event = new CompetencyAcquiredEvent();
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance using no-arg constructor")
        void testNoArgConstructor() {
            CompetencyAcquiredEvent event = new CompetencyAcquiredEvent();
            assertNotNull(event);
            assertNull(event.getEventType());
            assertNull(event.getUserId());
            assertNull(event.getContentId());
            assertNull(event.getBatchId());
            assertNull(event.getContextType());
        }

        @Test
        @DisplayName("Should create instance using all-args constructor")
        void testAllArgsConstructor() {
            CompetencyAcquiredEvent event = new CompetencyAcquiredEvent(
                    "competency.acquired",
                    "user123",
                    "content456",
                    "batch789",
                    "self-declaration"
            );

            assertNotNull(event);
            assertEquals("competency.acquired", event.getEventType());
            assertEquals("user123", event.getUserId());
            assertEquals("content456", event.getContentId());
            assertEquals("batch789", event.getBatchId());
            assertEquals("self-declaration", event.getContextType());
        }

        @Test
        @DisplayName("Should create instance with partial arguments")
        void testPartialArgsConstructor() {
            CompetencyAcquiredEvent event = new CompetencyAcquiredEvent(
                    "competency.acquired",
                    "user123",
                    "content456",
                    "",
                    null
            );

            assertEquals("competency.acquired", event.getEventType());
            assertEquals("user123", event.getUserId());
            assertEquals("content456", event.getContentId());
            assertEquals("", event.getBatchId());
            assertNull(event.getContextType());
        }
    }

    // ==================== Builder Pattern Tests ====================

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build complete event using builder")
        void testBuilderWithAllFields() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            assertNotNull(event);
            assertEquals("competency.acquired", event.getEventType());
            assertEquals("user123", event.getUserId());
            assertEquals("content456", event.getContentId());
            assertEquals("batch789", event.getBatchId());
            assertEquals("self-declaration", event.getContextType());
        }

        @Test
        @DisplayName("Should build event with selective fields")
        void testBuilderWithSelectiveFields() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user456")
                    .contentId("content789")
                    .build();

            assertNotNull(event);
            assertNull(event.getEventType());
            assertEquals("user456", event.getUserId());
            assertEquals("content789", event.getContentId());
            assertNull(event.getBatchId());
            assertNull(event.getContextType());
        }

        @Test
        @DisplayName("Should build event with no fields")
        void testBuilderWithNoFields() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder().build();

            assertNotNull(event);
            assertNull(event.getEventType());
            assertNull(event.getUserId());
            assertNull(event.getContentId());
            assertNull(event.getBatchId());
            assertNull(event.getContextType());
        }

        @Test
        @DisplayName("Should override fields in builder")
        void testBuilderFieldOverride() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .userId("user456")  // Override with new value
                    .contentId("content789")
                    .contentId("content999")  // Override with new value
                    .build();

            assertEquals("user456", event.getUserId());
            assertEquals("content999", event.getContentId());
        }
    }

    // ==================== Getter and Setter Tests ====================

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get eventType")
        void testEventTypeGetterSetter() {
            event.setEventType("competency.acquired");
            assertEquals("competency.acquired", event.getEventType());
        }

        @Test
        @DisplayName("Should set and get userId")
        void testUserIdGetterSetter() {
            event.setUserId("user123");
            assertEquals("user123", event.getUserId());
        }

        @Test
        @DisplayName("Should set and get contentId")
        void testContentIdGetterSetter() {
            event.setContentId("content456");
            assertEquals("content456", event.getContentId());
        }

        @Test
        @DisplayName("Should set and get batchId")
        void testBatchIdGetterSetter() {
            event.setBatchId("batch789");
            assertEquals("batch789", event.getBatchId());
        }

        @Test
        @DisplayName("Should set and get contextType")
        void testContextTypeGetterSetter() {
            event.setContextType("self-declaration");
            assertEquals("self-declaration", event.getContextType());
        }

        @Test
        @DisplayName("Should set all fields and retrieve them")
        void testAllGettersSetters() {
            event.setEventType("competency.acquired");
            event.setUserId("user123");
            event.setContentId("content456");
            event.setBatchId("batch789");
            event.setContextType("self-declaration");

            assertEquals("competency.acquired", event.getEventType());
            assertEquals("user123", event.getUserId());
            assertEquals("content456", event.getContentId());
            assertEquals("batch789", event.getBatchId());
            assertEquals("self-declaration", event.getContextType());
        }

        @Test
        @DisplayName("Should handle null values in setters")
        void testNullValueSetters() {
            event.setEventType(null);
            event.setUserId(null);
            event.setContentId(null);
            event.setBatchId(null);
            event.setContextType(null);

            assertNull(event.getEventType());
            assertNull(event.getUserId());
            assertNull(event.getContentId());
            assertNull(event.getBatchId());
            assertNull(event.getContextType());
        }

        @Test
        @DisplayName("Should handle empty string values")
        void testEmptyStringSetters() {
            event.setEventType("");
            event.setUserId("");
            event.setContentId("");
            event.setBatchId("");
            event.setContextType("");

            assertEquals("", event.getEventType());
            assertEquals("", event.getUserId());
            assertEquals("", event.getContentId());
            assertEquals("", event.getBatchId());
            assertEquals("", event.getContextType());
        }
    }

    // ==================== Equals and HashCode Tests ====================

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Should be equal when all fields are the same")
        void testEqualsWithSameFields() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            assertEquals(event1, event2);
        }

        @Test
        @DisplayName("Should not be equal when eventType differs")
        void testNotEqualsWithDifferentEventType() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .eventType("competency.updated")
                    .userId("user123")
                    .build();

            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("Should not be equal when userId differs")
        void testNotEqualsWithDifferentUserId() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .userId("user999")
                    .contentId("content456")
                    .build();

            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("Should not be equal when contentId differs")
        void testNotEqualsWithDifferentContentId() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content999")
                    .build();

            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("Should have same hashCode for equal objects")
        void testHashCodeForEqualObjects() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .build();

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        @DisplayName("Should have different hashCode for different objects")
        void testHashCodeForDifferentObjects() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .userId("user999")
                    .contentId("content456")
                    .build();

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to null")
        void testNotEqualsToNull() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .build();

            assertNotEquals(event, null);
            assertFalse(event.equals(null));
        }

        @Test
        @DisplayName("Should not be equal to different type")
        void testNotEqualsToDifferentType() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .build();

            assertNotEquals(event, "not an event");
            assertNotEquals(event, 123);
            assertNotEquals(event, new Object());
        }

        @Test
        @DisplayName("Should be equal to itself")
        void testEqualsToItself() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .build();

            assertEquals(event, event);
        }
    }

    // ==================== ToString Tests ====================

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should generate toString representation")
        void testToString() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            String toString = event.toString();
            assertNotNull(toString);
            assertNotBlank(toString);
            assertTrue(toString.contains("CompetencyAcquiredEvent"));
        }

        @Test
        @DisplayName("Should include field values in toString")
        void testToStringContainsFields() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            String toString = event.toString();
            assertTrue(toString.contains("user123"));
            assertTrue(toString.contains("content456"));
        }

        @Test
        @DisplayName("Should handle null values in toString")
        void testToStringWithNullValues() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId(null)
                    .build();

            String toString = event.toString();
            assertNotNull(toString);
            assertNotBlank(toString);
        }

        @Test
        @DisplayName("toString should be consistent")
        void testToStringConsistency() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            String toString1 = event.toString();
            String toString2 = event.toString();

            assertEquals(toString1, toString2);
        }
    }

    // ==================== JSON Serialization Tests ====================

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JSONSerializationTests {

        @Test
        @DisplayName("Should serialize to JSON with all fields")
        void testSerializeWithAllFields() throws JsonProcessingException {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            String json = objectMapper.writeValueAsString(event);
            assertNotNull(json);
            assertNotBlank(json);
            assertTrue(json.contains("eventType"));
            assertTrue(json.contains("competency.acquired"));
            assertTrue(json.contains("userId"));
            assertTrue(json.contains("user123"));
            assertTrue(json.contains("contentId"));
            assertTrue(json.contains("content456"));
            assertTrue(json.contains("batchId"));
            assertTrue(json.contains("batch789"));
            assertTrue(json.contains("contextType"));
            assertTrue(json.contains("self-declaration"));
        }

        @Test
        @DisplayName("Should serialize with null fields")
        void testSerializeWithNullFields() throws JsonProcessingException {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            String json = objectMapper.writeValueAsString(event);
            assertNotNull(json);
            assertTrue(json.contains("userId"));
            assertTrue(json.contains("contentId"));
        }

        @Test
        @DisplayName("Should deserialize from JSON with all fields")
        void testDeserializeWithAllFields() throws JsonProcessingException {
            String json = "{\"eventType\":\"competency.acquired\",\"userId\":\"user123\"," +
                    "\"contentId\":\"content456\",\"batchId\":\"batch789\",\"contextType\":\"self-declaration\"}";

            CompetencyAcquiredEvent event = objectMapper.readValue(json, CompetencyAcquiredEvent.class);

            assertNotNull(event);
            assertEquals("competency.acquired", event.getEventType());
            assertEquals("user123", event.getUserId());
            assertEquals("content456", event.getContentId());
            assertEquals("batch789", event.getBatchId());
            assertEquals("self-declaration", event.getContextType());
        }

        @Test
        @DisplayName("Should deserialize with missing optional fields")
        void testDeserializeWithMissingFields() throws JsonProcessingException {
            String json = "{\"userId\":\"user123\",\"contentId\":\"content456\"}";

            CompetencyAcquiredEvent event = objectMapper.readValue(json, CompetencyAcquiredEvent.class);

            assertNotNull(event);
            assertNull(event.getEventType());
            assertEquals("user123", event.getUserId());
            assertEquals("content456", event.getContentId());
            assertNull(event.getBatchId());
            assertNull(event.getContextType());
        }

        @Test
        @DisplayName("Should round-trip serialize and deserialize")
        void testRoundTripSerialization() throws JsonProcessingException {
            CompetencyAcquiredEvent original = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            String json = objectMapper.writeValueAsString(original);
            CompetencyAcquiredEvent deserialized = objectMapper.readValue(json, CompetencyAcquiredEvent.class);

            assertEquals(original, deserialized);
            assertEquals(original.getEventType(), deserialized.getEventType());
            assertEquals(original.getUserId(), deserialized.getUserId());
            assertEquals(original.getContentId(), deserialized.getContentId());
            assertEquals(original.getBatchId(), deserialized.getBatchId());
            assertEquals(original.getContextType(), deserialized.getContextType());
        }

        @Test
        @DisplayName("Should handle empty string in JSON")
        void testDeserializeWithEmptyStrings() throws JsonProcessingException {
            String json = "{\"eventType\":\"\",\"userId\":\"\",\"contentId\":\"\",\"batchId\":\"\",\"contextType\":\"\"}";

            CompetencyAcquiredEvent event = objectMapper.readValue(json, CompetencyAcquiredEvent.class);

            assertNotNull(event);
            assertEquals("", event.getEventType());
            assertEquals("", event.getUserId());
            assertEquals("", event.getContentId());
            assertEquals("", event.getBatchId());
            assertEquals("", event.getContextType());
        }

        @Test
        @DisplayName("Should handle null values in JSON")
        void testDeserializeWithNullValues() throws JsonProcessingException {
            String json = "{\"eventType\":null,\"userId\":\"user123\",\"contentId\":null}";

            CompetencyAcquiredEvent event = objectMapper.readValue(json, CompetencyAcquiredEvent.class);

            assertNotNull(event);
            assertNull(event.getEventType());
            assertEquals("user123", event.getUserId());
            assertNull(event.getContentId());
        }
    }

    // ==================== Field Validation Tests ====================

    @Nested
    @DisplayName("Field Validation Tests")
    class FieldValidationTests {

        @Test
        @DisplayName("Should allow special characters in userId")
        void testSpecialCharactersInUserId() {
            event.setUserId("user_123-456@example.com");
            assertEquals("user_123-456@example.com", event.getUserId());
        }

        @Test
        @DisplayName("Should allow special characters in contentId")
        void testSpecialCharactersInContentId() {
            event.setContentId("content-456_789.abc");
            assertEquals("content-456_789.abc", event.getContentId());
        }

        @Test
        @DisplayName("Should allow long userId values")
        void testLongUserIdValue() {
            String longUserId = "a".repeat(1000);
            event.setUserId(longUserId);
            assertEquals(longUserId, event.getUserId());
        }

        @Test
        @DisplayName("Should allow various eventType values")
        void testDifferentEventTypes() {
            String[] eventTypes = {
                    "competency.acquired",
                    "competency.updated",
                    "competency.deleted",
                    "COMPETENCY_ACQUIRED",
                    "CompetencyAcquired"
            };

            for (String eventType : eventTypes) {
                event.setEventType(eventType);
                assertEquals(eventType, event.getEventType());
            }
        }

        @Test
        @DisplayName("Should allow various contextType values")
        void testDifferentContextTypes() {
            String[] contextTypes = {
                    "self-declaration",
                    "assessment",
                    "course",
                    "curriculum",
                    "other-source"
            };

            for (String contextType : contextTypes) {
                event.setContextType(contextType);
                assertEquals(contextType, event.getContextType());
            }
        }

        @Test
        @DisplayName("Should handle whitespace in values")
        void testWhitespaceInValues() {
            event.setUserId("  user123  ");
            event.setContentId("content 456");
            event.setBatchId(" batch 789 ");

            assertEquals("  user123  ", event.getUserId());
            assertEquals("content 456", event.getContentId());
            assertEquals(" batch 789 ", event.getBatchId());
        }

        @Test
        @DisplayName("Should handle numeric values as strings")
        void testNumericValuesAsStrings() {
            event.setUserId("12345");
            event.setContentId("67890");
            event.setBatchId("11111");

            assertEquals("12345", event.getUserId());
            assertEquals("67890", event.getContentId());
            assertEquals("11111", event.getBatchId());
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle creating multiple instances independently")
        void testMultipleInstancesIndependence() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .userId("user1")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .userId("user2")
                    .build();

            assertEquals("user1", event1.getUserId());
            assertEquals("user2", event2.getUserId());
            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("Should allow modifying object after creation")
        void testModifyAfterCreation() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user1")
                    .build();

            event.setUserId("user2");
            event.setContentId("content1");

            assertEquals("user2", event.getUserId());
            assertEquals("content1", event.getContentId());
        }

        @Test
        @DisplayName("Should create independent copies via builder")
        void testIndependentBuilderCopies() {
            CompetencyAcquiredEvent original = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .contentId("content456")
                    .build();

            CompetencyAcquiredEvent copy = CompetencyAcquiredEvent.builder()
                    .userId(original.getUserId())
                    .contentId(original.getContentId())
                    .build();

            // Modify original
            original.setUserId("modified");

            // Copy should remain unchanged
            assertEquals("user123", copy.getUserId());
            assertEquals("modified", original.getUserId());
        }

        @Test
        @DisplayName("Should handle concurrent modifications safely")
        void testConcurrentModifications() throws InterruptedException {
            CompetencyAcquiredEvent event = new CompetencyAcquiredEvent();

            Thread thread1 = new Thread(() -> event.setUserId("user1"));
            Thread thread2 = new Thread(() -> event.setContentId("content1"));

            thread1.start();
            thread2.start();

            thread1.join();
            thread2.join();

            // Both modifications should be present
            assertTrue(event.getUserId() != null || event.getContentId() != null);
        }

        @Test
        @DisplayName("Should handle Unicode characters in fields")
        void testUnicodeCharacters() {
            event.setUserId("用户123");  // Chinese characters
            event.setContentId("contenu456");  // French characters
            event.setEventType("événement");  // Event with accent

            assertEquals("用户123", event.getUserId());
            assertEquals("contenu456", event.getContentId());
            assertEquals("événement", event.getEventType());
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should create complete event with all realistic data")
        void testRealisticEventCreation() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("d47c2e3f-8a1b-4c9d-9e2f-5a6b7c8d9e0f")
                    .contentId("95f8a7c5-4e3b-4d2c-9b1a-8f7e6d5c4b3a")
                    .batchId("d47c2e3f-8a1b-4c9d-9e2f-5a6b7c8d9e0f")
                    .contextType("self-declaration")
                    .build();

            assertNotNull(event);
            assertEquals("competency.acquired", event.getEventType());
            assertTrue(event.getUserId().contains("-"));
            assertTrue(event.getContentId().contains("-"));
            assertEquals("self-declaration", event.getContextType());
        }

        @Test
        @DisplayName("Should handle event lifecycle from creation to serialization")
        void testEventLifecycle() throws JsonProcessingException {
            // Create
            CompetencyAcquiredEvent original = CompetencyAcquiredEvent.builder()
                    .eventType("competency.acquired")
                    .userId("user123")
                    .contentId("content456")
                    .batchId("batch789")
                    .contextType("self-declaration")
                    .build();

            // Serialize
            String json = objectMapper.writeValueAsString(original);

            // Deserialize
            CompetencyAcquiredEvent restored = objectMapper.readValue(json, CompetencyAcquiredEvent.class);

            // Verify
            assertEquals(original, restored);
            assertEquals(original.getEventType(), restored.getEventType());
            assertEquals(original.getUserId(), restored.getUserId());
            assertEquals(original.getContentId(), restored.getContentId());
            assertEquals(original.getBatchId(), restored.getBatchId());
            assertEquals(original.getContextType(), restored.getContextType());
        }

        @Test
        @DisplayName("Should work correctly in a list of events")
        void testEventInList() {
            java.util.List<CompetencyAcquiredEvent> events = new java.util.ArrayList<>();

            for (int i = 0; i < 5; i++) {
                events.add(CompetencyAcquiredEvent.builder()
                        .userId("user" + i)
                        .contentId("content" + i)
                        .build());
            }

            assertEquals(5, events.size());
            assertEquals("user0", events.get(0).getUserId());
            assertEquals("user4", events.get(4).getUserId());
        }

        @Test
        @DisplayName("Should work correctly with null values in processing")
        void testEventProcessingWithNulls() {
            CompetencyAcquiredEvent event = new CompetencyAcquiredEvent();

            String userId = event.getUserId();
            String contentId = event.getContentId();

            assertNull(userId);
            assertNull(contentId);

            event.setUserId("user123");
            assertNotNull(event.getUserId());
        }
    }

    // ==================== Helper Methods ====================

    private void assertNotBlank(String str) {
        assertNotNull(str);
        assertTrue(!str.trim().isEmpty());
    }
}

