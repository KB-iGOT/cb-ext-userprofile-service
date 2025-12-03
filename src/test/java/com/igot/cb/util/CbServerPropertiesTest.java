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
}

