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
    void testGetBasicProfileFields() throws Exception {
        setField("basicProfileFields", "name,email,phone");

        List<String> basicFields = properties.getBasicProfileFields();

        assertEquals(3, basicFields.size());
        assertEquals("name", basicFields.get(0));
        assertEquals("email", basicFields.get(1));
        assertEquals("phone", basicFields.get(2));
    }

    @Test
    void testGetProfileCompletionRequiredFields() throws Exception {
        setField("profileCompletionRequiredFields", "field1,field2,field3");

        List<String> requiredFields = properties.getProfileCompletionRequiredFields();

        assertEquals(3, requiredFields.size());
        assertEquals("field1", requiredFields.get(0));
        assertEquals("field2", requiredFields.get(1));
        assertEquals("field3", requiredFields.get(2));
    }

    @Test
    void testRedisInsightIndex() {
        properties.setRedisInsightIndex(5);
        assertEquals(5, properties.getRedisInsightIndex());
    }

    @Test
    void testSearchResultRedisTtl() {
        properties.setSearchResultRedisTtl(100L);
        assertEquals(100L, properties.getSearchResultRedisTtl());
    }

    @Test
    void testSbApiKey() {
        properties.setSbApiKey("test-api-key");
        assertEquals("test-api-key", properties.getSbApiKey());
    }

    @Test
    void testRequestTimeoutMs() {
        properties.setRequestTimeoutMs(3000);
        assertEquals(3000, properties.getRequestTimeoutMs());
    }

    @Test
    void testMaxTotalConnections() {
        properties.setMaxTotalConnections(200);
        assertEquals(200, properties.getMaxTotalConnections());
    }

    @Test
    void testMaxConnectionsPerRoute() {
        properties.setMaxConnectionsPerRoute(50);
        assertEquals(50, properties.getMaxConnectionsPerRoute());
    }

    @Test
    void testRedisPoolMaxTotal() {
        properties.setRedisPoolMaxTotal(10);
        assertEquals(10, properties.getRedisPoolMaxTotal());
    }

    @Test
    void testRedisPoolMaxIdle() {
        properties.setRedisPoolMaxIdle(5);
        assertEquals(5, properties.getRedisPoolMaxIdle());
    }

    @Test
    void testRedisPoolMinIdle() {
        properties.setRedisPoolMinIdle(2);
        assertEquals(2, properties.getRedisPoolMinIdle());
    }

    @Test
    void testRedisPoolMaxWait() {
        properties.setRedisPoolMaxWait(1000);
        assertEquals(1000, properties.getRedisPoolMaxWait());
    }

    @Test
    void testRedisConnectionTimeout() {
        properties.setRedisConnectionTimeout(5000L);
        assertEquals(5000L, properties.getRedisConnectionTimeout());
    }

    @Test
    void testEducationalQualificationMandatoryFields() {
        properties.setEducationalQualificationMandatoryFields("degree,institution");
        assertEquals("degree,institution", properties.getEducationalQualificationMandatoryFields());
    }

    @Test
    void testServiceHistoryMandatoryFields() {
        properties.setServiceHistoryMandatoryFields("position,company");
        assertEquals("position,company", properties.getServiceHistoryMandatoryFields());
    }

    @Test
    void testAchievementsMandatoryFields() {
        properties.setAchievementsMandatoryFields("title,description");
        assertEquals("title,description", properties.getAchievementsMandatoryFields());
    }

    @Test
    void testContextType() {
        String[] contextTypes = {"education", "experience", "achievement"};
        properties.setContextType(contextTypes);
        assertArrayEquals(contextTypes, properties.getContextType());
    }

    @Test
    void testBasicProfileFieldsSetterGetter() {
        properties.setBasicProfileFields("name,email,phone");
        // Note: getBasicProfileFields() returns a List, not a String
        // So we test the setter with a different approach
        List<String> fields = properties.getBasicProfileFields();
        assertEquals(3, fields.size());
        assertTrue(fields.contains("name"));
        assertTrue(fields.contains("email"));
        assertTrue(fields.contains("phone"));
    }

    @Test
    void testProfileCompletionRequiredFieldsSetterGetter() {
        properties.setProfileCompletionRequiredFields("field1,field2");
        // Note: getProfileCompletionRequiredFields() returns a List, not a String
        // So we test the setter with a different approach
        List<String> fields = properties.getProfileCompletionRequiredFields();
        assertEquals(2, fields.size());
        assertTrue(fields.contains("field1"));
        assertTrue(fields.contains("field2"));
    }

    @Test
    void testExtendedFieldsConfig() {
        List<String> extendedFields = List.of("ext1", "ext2", "ext3");
        properties.setExtendedFieldsConfig(extendedFields);
        assertEquals(extendedFields, properties.getExtendedFieldsConfig());
    }

    @Test
    void testFieldWeight() {
        properties.setFieldWeight(0.5);
        assertEquals(0.5, properties.getFieldWeight());
    }

    @Test
    void testCommunityBaseUrl() {
        properties.setCommunityBaseUrl("http://community.example.com");
        assertEquals("http://community.example.com", properties.getCommunityBaseUrl());
    }

    @Test
    void testCommunityPostCountApiUrl() {
        properties.setCommunityPostCountApiUrl("/api/posts/count");
        assertEquals("/api/posts/count", properties.getCommunityPostCountApiUrl());
    }

    @Test
    void testRedisTimeout() {
        properties.setRedisTimeout("5000");
        assertEquals("5000", properties.getRedisTimeout());
    }

    @Test
    void testRedisHostName() {
        properties.setRedisHostName("localhost");
        assertEquals("localhost", properties.getRedisHostName());
    }

    @Test
    void testRedisPort() {
        properties.setRedisPort("6379");
        assertEquals("6379", properties.getRedisPort());
    }

    @Test
    void testRedisDataHostName() {
        properties.setRedisDataHostName("redis-data.example.com");
        assertEquals("redis-data.example.com", properties.getRedisDataHostName());
    }

    @Test
    void testRedisDataPort() {
        properties.setRedisDataPort("6380");
        assertEquals("6380", properties.getRedisDataPort());
    }

    @Test
    void testRedisMaxIdle() {
        properties.setRedisMaxIdle(8);
        assertEquals(8, properties.getRedisMaxIdle());
    }

    @Test
    void testRedisMaxTotal() {
        properties.setRedisMaxTotal(20);
        assertEquals(20, properties.getRedisMaxTotal());
    }

    @Test
    void testRedisMinIdle() {
        properties.setRedisMinIdle(3);
        assertEquals(3, properties.getRedisMinIdle());
    }

    @Test
    void testRedisTestOnBorrow() {
        properties.setRedisTestOnBorrow(true);
        assertTrue(properties.getRedisTestOnBorrow());

        properties.setRedisTestOnBorrow(false);
        assertFalse(properties.getRedisTestOnBorrow());
    }

    @Test
    void testRedisTestOnReturn() {
        properties.setRedisTestOnReturn(true);
        assertTrue(properties.getRedisTestOnReturn());

        properties.setRedisTestOnReturn(false);
        assertFalse(properties.getRedisTestOnReturn());
    }

    @Test
    void testRedisTestWhileIdle() {
        properties.setRedisTestWhileIdle(true);
        assertTrue(properties.getRedisTestWhileIdle());

        properties.setRedisTestWhileIdle(false);
        assertFalse(properties.getRedisTestWhileIdle());
    }

    @Test
    void testRedisMinEvictableIdleTimeMillis() {
        properties.setRedisMinEvictableIdleTimeMillis(60000L);
        assertEquals(60000L, properties.getRedisMinEvictableIdleTimeMillis());
    }

    @Test
    void testRedisTimeBetweenEvictionRunsMillis() {
        properties.setRedisTimeBetweenEvictionRunsMillis(30000L);
        assertEquals(30000L, properties.getRedisTimeBetweenEvictionRunsMillis());
    }

    @Test
    void testRedisNumTestsPerEvictionRun() {
        properties.setRedisNumTestsPerEvictionRun(5);
        assertEquals(5, properties.getRedisNumTestsPerEvictionRun());
    }

    @Test
    void testRedisBlockWhenExhausted() {
        properties.setRedisBlockWhenExhausted(true);
        assertTrue(properties.getRedisBlockWhenExhausted());

        properties.setRedisBlockWhenExhausted(false);
        assertFalse(properties.getRedisBlockWhenExhausted());
    }

    @Test
    void testDataIndex() {
        properties.setDataIndex(1);
        assertEquals(1, properties.getDataIndex());
    }

    @Test
    void testCacheTtl() {
        properties.setCacheTtl(3600);
        assertEquals(3600, properties.getCacheTtl());
    }

    @Test
    void testCertificateCountRedisKey() {
        properties.setCertificateCountRedisKey("certificate:count");
        assertEquals("certificate:count", properties.getCertificateCountRedisKey());
    }

    @Test
    void testUserProfileIndex() {
        properties.userProfileIndex = "user-profile-index";
        assertEquals("user-profile-index", properties.userProfileIndex);
    }

    @Test
    void testHubGraphService() {
        properties.hubGraphService = "http://hub-graph.example.com";
        assertEquals("http://hub-graph.example.com", properties.hubGraphService);
    }

    @Test
    void testConnectionApi() {
        properties.connectionApi = "/api/connections";
        assertEquals("/api/connections", properties.connectionApi);
    }

    @Test
    void testUserEnrolmentsTable() {
        properties.setUserEnrolmentsTable("user_enrolments");
        assertEquals("user_enrolments", properties.getUserEnrolmentsTable());
    }

    @Test
    void testBasicDetailsFilteredKeys() {
        properties.setBasicDetailsFilteredKeys("password,token,secret");
        assertEquals("password,token,secret", properties.getBasicDetailsFilteredKeys());
    }

    @Test
    void testProfileVisibleAllowedFields() {
        properties.setProfileVisibleAllowedFields("name,email,department");
        assertEquals("name,email,department", properties.getProfileVisibleAllowedFields());
    }

    @Test
    void testGetBasicProfileFields_WithEmptyString() throws Exception {
        setField("basicProfileFields", "");

        List<String> basicFields = properties.getBasicProfileFields();

        assertEquals(1, basicFields.size());
        assertEquals("", basicFields.get(0));
    }

    @Test
    void testGetBasicProfileFields_WithSingleField() throws Exception {
        setField("basicProfileFields", "email");

        List<String> basicFields = properties.getBasicProfileFields();

        assertEquals(1, basicFields.size());
        assertEquals("email", basicFields.get(0));
    }

    @Test
    void testGetProfileCompletionRequiredFields_WithEmptyString() throws Exception {
        setField("profileCompletionRequiredFields", "");

        List<String> requiredFields = properties.getProfileCompletionRequiredFields();

        assertEquals(1, requiredFields.size());
        assertEquals("", requiredFields.get(0));
    }

    @Test
    void testGetProfileCompletionRequiredFields_WithSingleField() throws Exception {
        setField("profileCompletionRequiredFields", "firstName");

        List<String> requiredFields = properties.getProfileCompletionRequiredFields();

        assertEquals(1, requiredFields.size());
        assertEquals("firstName", requiredFields.get(0));
    }

    @Test
    void testGetBasicProfileFields_WithMultipleFieldsAndSpaces() throws Exception {
        setField("basicProfileFields", " name , email , phone ");

        List<String> basicFields = properties.getBasicProfileFields();

        assertEquals(3, basicFields.size());
        assertEquals(" name ", basicFields.get(0));
        assertEquals(" email ", basicFields.get(1));
        assertEquals(" phone ", basicFields.get(2));
    }

    @Test
    void testAllRedisConfigurationPropertiesTogether() {
        // Set all Redis configuration properties
        properties.setRedisHostName("redis.example.com");
        properties.setRedisPort("6379");
        properties.setRedisDataHostName("redis-data.example.com");
        properties.setRedisDataPort("6380");
        properties.setRedisTimeout("5000");
        properties.setRedisMaxIdle(10);
        properties.setRedisMaxTotal(50);
        properties.setRedisMinIdle(5);
        properties.setRedisTestOnBorrow(true);
        properties.setRedisTestOnReturn(false);
        properties.setRedisTestWhileIdle(true);
        properties.setRedisMinEvictableIdleTimeMillis(60000L);
        properties.setRedisTimeBetweenEvictionRunsMillis(30000L);
        properties.setRedisNumTestsPerEvictionRun(3);
        properties.setRedisBlockWhenExhausted(true);

        // Verify all properties are set correctly
        assertEquals("redis.example.com", properties.getRedisHostName());
        assertEquals("6379", properties.getRedisPort());
        assertEquals("redis-data.example.com", properties.getRedisDataHostName());
        assertEquals("6380", properties.getRedisDataPort());
        assertEquals("5000", properties.getRedisTimeout());
        assertEquals(10, properties.getRedisMaxIdle());
        assertEquals(50, properties.getRedisMaxTotal());
        assertEquals(5, properties.getRedisMinIdle());
        assertTrue(properties.getRedisTestOnBorrow());
        assertFalse(properties.getRedisTestOnReturn());
        assertTrue(properties.getRedisTestWhileIdle());
        assertEquals(60000L, properties.getRedisMinEvictableIdleTimeMillis());
        assertEquals(30000L, properties.getRedisTimeBetweenEvictionRunsMillis());
        assertEquals(3, properties.getRedisNumTestsPerEvictionRun());
        assertTrue(properties.getRedisBlockWhenExhausted());
    }

    @Test
    void testAllPoolConfigurationPropertiesTogether() {
        // Set all pool configuration properties
        properties.setRedisPoolMaxTotal(100);
        properties.setRedisPoolMaxIdle(20);
        properties.setRedisPoolMinIdle(5);
        properties.setRedisPoolMaxWait(2000);
        properties.setRedisConnectionTimeout(10000L);

        // Verify all properties are set correctly
        assertEquals(100, properties.getRedisPoolMaxTotal());
        assertEquals(20, properties.getRedisPoolMaxIdle());
        assertEquals(5, properties.getRedisPoolMinIdle());
        assertEquals(2000, properties.getRedisPoolMaxWait());
        assertEquals(10000L, properties.getRedisConnectionTimeout());
    }

    @Test
    void testAllValidationFieldsTogether() {
        // Set all validation fields
        properties.setEducationalQualificationMandatoryFields("degree,university,year");
        properties.setServiceHistoryMandatoryFields("position,organization,duration");
        properties.setAchievementsMandatoryFields("title,date,description");

        // Verify all properties are set correctly
        assertEquals("degree,university,year", properties.getEducationalQualificationMandatoryFields());
        assertEquals("position,organization,duration", properties.getServiceHistoryMandatoryFields());
        assertEquals("title,date,description", properties.getAchievementsMandatoryFields());
    }

    @Test
    void testNullSafetyForIntegerProperties() {
        // Test that Integer properties can handle null
        properties.setRedisMaxIdle(null);
        assertNull(properties.getRedisMaxIdle());

        properties.setRedisMaxTotal(null);
        assertNull(properties.getRedisMaxTotal());

        properties.setRedisMinIdle(null);
        assertNull(properties.getRedisMinIdle());

        properties.setRedisNumTestsPerEvictionRun(null);
        assertNull(properties.getRedisNumTestsPerEvictionRun());
    }

    @Test
    void testNullSafetyForBooleanProperties() {
        // Test that Boolean properties can handle null
        properties.setRedisTestOnBorrow(null);
        assertNull(properties.getRedisTestOnBorrow());

        properties.setRedisTestOnReturn(null);
        assertNull(properties.getRedisTestOnReturn());

        properties.setRedisTestWhileIdle(null);
        assertNull(properties.getRedisTestWhileIdle());

        properties.setRedisBlockWhenExhausted(null);
        assertNull(properties.getRedisBlockWhenExhausted());
    }

    @Test
    void testNullSafetyForLongProperties() {
        // Test that Long properties can handle null
        properties.setRedisMinEvictableIdleTimeMillis(null);
        assertNull(properties.getRedisMinEvictableIdleTimeMillis());

        properties.setRedisTimeBetweenEvictionRunsMillis(null);
        assertNull(properties.getRedisTimeBetweenEvictionRunsMillis());
    }

    @Test
    void testExtendedFieldsConfig_WithEmptyList() {
        List<String> emptyList = List.of();
        properties.setExtendedFieldsConfig(emptyList);
        assertEquals(emptyList, properties.getExtendedFieldsConfig());
        assertTrue(properties.getExtendedFieldsConfig().isEmpty());
    }

    @Test
    void testExtendedFieldsConfig_WithMultipleFields() {
        List<String> fields = List.of("education", "experience", "skills", "certifications");
        properties.setExtendedFieldsConfig(fields);
        assertEquals(4, properties.getExtendedFieldsConfig().size());
        assertEquals(fields, properties.getExtendedFieldsConfig());
    }

    @Test
    void testFieldWeight_WithDifferentValues() {
        properties.setFieldWeight(0.0);
        assertEquals(0.0, properties.getFieldWeight());

        properties.setFieldWeight(1.0);
        assertEquals(1.0, properties.getFieldWeight());

        properties.setFieldWeight(0.75);
        assertEquals(0.75, properties.getFieldWeight());

        properties.setFieldWeight(-0.5);
        assertEquals(-0.5, properties.getFieldWeight());
    }

    @Test
    void testContextType_WithSingleElement() {
        String[] singleContextType = {"education"};
        properties.setContextType(singleContextType);
        assertArrayEquals(singleContextType, properties.getContextType());
        assertEquals(1, properties.getContextType().length);
    }

    @Test
    void testContextType_WithEmptyArray() {
        String[] emptyArray = {};
        properties.setContextType(emptyArray);
        assertArrayEquals(emptyArray, properties.getContextType());
        assertEquals(0, properties.getContextType().length);
    }
}

