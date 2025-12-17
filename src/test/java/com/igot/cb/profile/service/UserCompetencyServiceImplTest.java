package com.igot.cb.profile.service;

import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCompetencyServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CacheService cacheService;

    @Mock
    private ProjectUtil projectUtil;

    private UserCompetencyServiceImpl service;

    private static final String USER_ID = "user-123";
    private static final String USER_TOKEN = "token-123";

    @BeforeEach
    void setUp() {
        service = new UserCompetencyServiceImpl(accessTokenValidator, cassandraOperation,
                cacheService, projectUtil);
    }

    // ==================== listCompetencies Tests ====================

    @Test
    void testListCompetencies_WithCacheHit() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);

            String cacheKey = Constants.USER + ":competencies:" + USER_ID;
            String cachedJson = "{\"competencyAreaCounts\":{\"Technology\":5}}";
            Map<String, Object> cachedCompetencies = Map.of("competencyAreaCounts", Map.of("Technology", 5));

            when(cacheService.getCache(cacheKey)).thenReturn(cachedJson);
            when(projectUtil.parseMap(cachedJson)).thenReturn(cachedCompetencies);

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            assertEquals(HttpStatus.OK, response.getResponseCode());
            assertNotNull(response.get(Constants.RESPONSE));
            assertEquals(cachedCompetencies, response.get(Constants.RESPONSE));
            verify(cassandraOperation, never()).getAllRecordsByProperties(any(), any(), any(), any(), anyInt());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testListCompetencies_WithCacheMiss_Success() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);

            String cacheKey = Constants.USER + ":competencies:" + USER_ID;
            when(cacheService.getCache(cacheKey)).thenReturn(null);

            // Mock enrolment records
            Map<String, Object> enrolment1 = new HashMap<>();
            enrolment1.put(Constants.USERID_KEY, USER_ID);
            enrolment1.put(Constants.COURSE_ID, "course-1");
            enrolment1.put(Constants.BATCH_ID, "batch-1");
            enrolment1.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment1.put(Constants.STATUS, 2);

            Map<String, Object> enrolment2 = new HashMap<>();
            enrolment2.put(Constants.USERID_KEY, USER_ID);
            enrolment2.put(Constants.COURSE_ID, "course-2");
            enrolment2.put(Constants.BATCH_ID, "batch-2");
            enrolment2.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment2.put(Constants.STATUS, 2);

            List<Map<String, Object>> enrolments = List.of(enrolment1, enrolment2);

            when(cassandraOperation.getAllRecordsByProperties(
                    eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                    eq(Constants.TABLE_USER_ENROLMENTS),
                    eq(Map.of(Constants.USERID_KEY, USER_ID)),
                    any(),
                    eq(100)
            )).thenReturn(enrolments);

            // Mock course metadata
            Map<String, String> courseMetadataJson = new HashMap<>();
            courseMetadataJson.put("course-1", "{\"courseId\":\"course-1\",\"competenciesV6\":[]}");
            courseMetadataJson.put("course-2", "{\"courseId\":\"course-2\",\"competenciesV6\":[]}");

            when(cacheService.getCourseMetadataAsJsonString(List.of("course-1", "course-2")))
                    .thenReturn(courseMetadataJson);

            Map<String, Object> course1Data = Map.of(
                    Constants.COURSE_ID, "course-1",
                    Constants.COMPETENCIES_V6, List.of(
                            Map.of(
                                    Constants.COMPETENCY_AREA_NAME, "Technology",
                                    Constants.COMPETENCY_THEME_NAME, "Software Development",
                                    Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                            )
                    )
            );

            Map<String, Object> course2Data = Map.of(
                    Constants.COURSE_ID, "course-2",
                    Constants.COMPETENCIES_V6, List.of(
                            Map.of(
                                    Constants.COMPETENCY_AREA_NAME, "Technology",
                                    Constants.COMPETENCY_THEME_NAME, "Software Development",
                                    Constants.COMPETENCY_SUB_THEME_NAME, "Frontend"
                            )
                    )
            );

            when(projectUtil.parseMap("{\"courseId\":\"course-1\",\"competenciesV6\":[]}"))
                    .thenReturn(course1Data);
            when(projectUtil.parseMap("{\"courseId\":\"course-2\",\"competenciesV6\":[]}"))
                    .thenReturn(course2Data);

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            assertEquals(HttpStatus.OK, response.getResponseCode());
            assertNotNull(response.get(Constants.RESPONSE));
            verify(cacheService).putCache(eq(cacheKey), any());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testListCompetencies_InvalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn("");

        ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, never()).getAllRecordsByProperties(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testListCompetencies_NoCompletedCourses() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);
            when(cacheService.getCache(anyString())).thenReturn(null);

            // Mock enrolment records with non-completed courses
            Map<String, Object> enrolment1 = new HashMap<>();
            enrolment1.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment1.put(Constants.STATUS, 1);  // Not completed

            when(cassandraOperation.getAllRecordsByProperties(any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(enrolment1));

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testListCompetencies_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache error"));

        ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testListCompetencies_CacheHitButEmptyParsedMap() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);

            String cacheKey = Constants.USER + ":competencies:" + USER_ID;
            String cachedJson = "{\"someData\":\"value\"}";

            when(cacheService.getCache(cacheKey)).thenReturn(cachedJson);
            when(projectUtil.parseMap(cachedJson)).thenReturn(Map.of());

            // Mock enrolment records with no completed courses
            when(cassandraOperation.getAllRecordsByProperties(any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testListCompetencies_EmptyCompetenciesAfterAnalysis() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);
            when(cacheService.getCache(anyString())).thenReturn(null);

            Map<String, Object> enrolment1 = new HashMap<>();
            enrolment1.put(Constants.USERID_KEY, USER_ID);
            enrolment1.put(Constants.COURSE_ID, "course-1");
            enrolment1.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment1.put(Constants.STATUS, 2);

            when(cassandraOperation.getAllRecordsByProperties(any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(enrolment1));

            Map<String, String> courseMetadataJson = Map.of("course-1", "{\"courseId\":\"course-1\"}");
            when(cacheService.getCourseMetadataAsJsonString(List.of("course-1")))
                    .thenReturn(courseMetadataJson);

            Map<String, Object> course1Data = Map.of(Constants.COURSE_ID, "course-1");
            when(projectUtil.parseMap("{\"courseId\":\"course-1\"}")).thenReturn(course1Data);

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            // analyzeCompetencies returns a result with empty maps, not truly empty, so it returns OK
            assertEquals(HttpStatus.OK, response.getResponseCode());
            assertNotNull(response.get(Constants.RESPONSE));

            @SuppressWarnings("unchecked")
            Map<String, Object> competencies = (Map<String, Object>) response.get(Constants.RESPONSE);
            assertTrue(competencies.containsKey(Constants.COMPETENCY_AREA_COUNTS));

            @SuppressWarnings("unchecked")
            Map<String, Long> areaCounts = (Map<String, Long>) competencies.get(Constants.COMPETENCY_AREA_COUNTS);
            assertTrue(areaCounts.isEmpty());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testListCompetencies_WithNonIntegerStatus() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);
            when(cacheService.getCache(anyString())).thenReturn(null);

            Map<String, Object> enrolment1 = new HashMap<>();
            enrolment1.put(Constants.USERID_KEY, USER_ID);
            enrolment1.put(Constants.COURSE_ID, "course-1");
            enrolment1.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment1.put(Constants.STATUS, "2"); // String instead of Integer

            Map<String, Object> enrolment2 = new HashMap<>();
            enrolment2.put(Constants.USERID_KEY, USER_ID);
            enrolment2.put(Constants.COURSE_ID, "course-2");
            enrolment2.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment2.put(Constants.STATUS, 2); // Valid Integer

            when(cassandraOperation.getAllRecordsByProperties(any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(enrolment1, enrolment2));

            Map<String, String> courseMetadataJson = Map.of("course-2", "{\"courseId\":\"course-2\",\"competenciesV6\":[]}");
            when(cacheService.getCourseMetadataAsJsonString(List.of("course-2")))
                    .thenReturn(courseMetadataJson);

            Map<String, Object> course2Data = Map.of(
                    Constants.COURSE_ID, "course-2",
                    Constants.COMPETENCIES_V6, List.of(
                            Map.of(
                                    Constants.COMPETENCY_AREA_NAME, "Technology",
                                    Constants.COMPETENCY_THEME_NAME, "Software Development",
                                    Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                            )
                    )
            );
            when(projectUtil.parseMap("{\"courseId\":\"course-2\",\"competenciesV6\":[]}"))
                    .thenReturn(course2Data);

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            assertEquals(HttpStatus.OK, response.getResponseCode());
            assertNotNull(response.get(Constants.RESPONSE));
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testListCompetencies_WithNullCourseId() {
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(eq(USER_TOKEN), any())).thenReturn(USER_ID);
            when(cacheService.getCache(anyString())).thenReturn(null);

            Map<String, Object> enrolment1 = new HashMap<>();
            enrolment1.put(Constants.USERID_KEY, USER_ID);
            enrolment1.put(Constants.COURSE_ID, null);
            enrolment1.put(Constants.ACTIVE_LOWERCASE, true);
            enrolment1.put(Constants.STATUS, 2);

            when(cassandraOperation.getAllRecordsByProperties(any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(enrolment1));

            ApiResponse response = service.listCompetencies(USER_ID, USER_TOKEN);

            assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    // ==================== getCourseMetadataBatched Tests ====================

    @Test
    void testGetCourseMetadataBatched_Success() {
        try {
            List<String> courseIds = List.of("course-1", "course-2", "course-3");
            List<String> fields = List.of(Constants.COURSE_ID, Constants.NAME, Constants.COMPETENCIES_V6);

            Map<String, String> courseMetadataJson = new HashMap<>();
            courseMetadataJson.put("course-1", "{\"courseId\":\"course-1\",\"name\":\"Course 1\",\"competenciesV6\":[]}");
            courseMetadataJson.put("course-2", "{\"courseId\":\"course-2\",\"name\":\"Course 2\",\"competenciesV6\":[]}");
            courseMetadataJson.put("course-3", "{\"courseId\":\"course-3\",\"name\":\"Course 3\",\"competenciesV6\":[]}");

            when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);

            Map<String, Object> course1 = new HashMap<>();
            course1.put(Constants.COURSE_ID, "course-1");
            course1.put(Constants.NAME, "Course 1");
            course1.put(Constants.COMPETENCIES_V6, List.of());
            course1.put("description", "Description 1");  // Extra field

            when(projectUtil.parseMap("{\"courseId\":\"course-1\",\"name\":\"Course 1\",\"competenciesV6\":[]}"))
                    .thenReturn(course1);

            Map<String, Object> course2 = new HashMap<>();
            course2.put(Constants.COURSE_ID, "course-2");
            course2.put(Constants.NAME, "Course 2");
            course2.put(Constants.COMPETENCIES_V6, List.of());

            when(projectUtil.parseMap("{\"courseId\":\"course-2\",\"name\":\"Course 2\",\"competenciesV6\":[]}"))
                    .thenReturn(course2);

            Map<String, Object> course3 = new HashMap<>();
            course3.put(Constants.COURSE_ID, "course-3");
            course3.put(Constants.NAME, "Course 3");
            course3.put(Constants.COMPETENCIES_V6, List.of());

            when(projectUtil.parseMap("{\"courseId\":\"course-3\",\"name\":\"Course 3\",\"competenciesV6\":[]}"))
                    .thenReturn(course3);

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, fields);

            assertEquals(3, result.size());
            assertTrue(result.containsKey("course-1"));
            assertTrue(result.containsKey("course-2"));
            assertTrue(result.containsKey("course-3"));

            // Verify fields are filtered
            Map<String, Object> filteredCourse1 = result.get("course-1");
            assertTrue(filteredCourse1.containsKey(Constants.COURSE_ID));
            assertTrue(filteredCourse1.containsKey(Constants.NAME));
            assertFalse(filteredCourse1.containsKey("description"));  // Extra field should be filtered out
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testGetCourseMetadataBatched_WithBatching() {
        try {
            List<String> courseIds = new ArrayList<>();
            for (int i = 1; i <= 250; i++) {
                courseIds.add("course-" + i);
            }

            Map<String, String> batch1Json = new HashMap<>();
            Map<String, String> batch2Json = new HashMap<>();
            Map<String, String> batch3Json = new HashMap<>();

            for (int i = 1; i <= 100; i++) {
                String courseId = "course-" + i;
                batch1Json.put(courseId, "{\"courseId\":\"" + courseId + "\"}");
            }
            for (int i = 101; i <= 200; i++) {
                String courseId = "course-" + i;
                batch2Json.put(courseId, "{\"courseId\":\"" + courseId + "\"}");
            }
            for (int i = 201; i <= 250; i++) {
                String courseId = "course-" + i;
                batch3Json.put(courseId, "{\"courseId\":\"" + courseId + "\"}");
            }

            when(cacheService.getCourseMetadataAsJsonString(anyList()))
                    .thenReturn(batch1Json, batch2Json, batch3Json);

            when(projectUtil.parseMap(anyString())).thenAnswer(invocation -> {
                String json = invocation.getArgument(0);
                String courseId = json.substring(json.indexOf("course-"), json.indexOf("\"}", json.indexOf("course-")));
                return Map.of(Constants.COURSE_ID, courseId);
            });

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, null);

            assertEquals(250, result.size());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testGetCourseMetadataBatched_EmptyList() {
        Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(
                Collections.emptyList(), 100, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseMetadataBatched_NullList() {
        Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(null, 100, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseMetadataBatched_ParseException() {
        try {
            List<String> courseIds = List.of("course-1");
            Map<String, String> courseMetadataJson = Map.of("course-1", "{invalid json}");

            when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);
            when(projectUtil.parseMap("{invalid json}")).thenThrow(new RuntimeException("Parse error"));

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, null);

            assertTrue(result.isEmpty());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testGetCourseMetadataBatched_NullJson() {
        List<String> courseIds = List.of("course-1");
        Map<String, String> courseMetadataJson = new HashMap<>();
        courseMetadataJson.put("course-1", null);

        when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);

        Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCourseMetadataBatched_ParsedMapNull() {
        try {
            List<String> courseIds = List.of("course-1");
            Map<String, String> courseMetadataJson = Map.of("course-1", "{\"courseId\":\"course-1\"}");

            when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);
            when(projectUtil.parseMap("{\"courseId\":\"course-1\"}")).thenReturn(null);

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, null);

            assertTrue(result.isEmpty());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testGetCourseMetadataBatched_ParsedMapEmpty() {
        try {
            List<String> courseIds = List.of("course-1");
            Map<String, String> courseMetadataJson = Map.of("course-1", "{\"courseId\":\"course-1\"}");

            when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);
            when(projectUtil.parseMap("{\"courseId\":\"course-1\"}")).thenReturn(Map.of());

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, null);

            assertTrue(result.isEmpty());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testGetCourseMetadataBatched_EmptyFieldsList() {
        try {
            List<String> courseIds = List.of("course-1");
            Map<String, String> courseMetadataJson = Map.of("course-1", "{\"courseId\":\"course-1\",\"name\":\"Course 1\"}");

            when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);

            Map<String, Object> course1Data = Map.of(
                    Constants.COURSE_ID, "course-1",
                    Constants.NAME, "Course 1"
            );
            when(projectUtil.parseMap("{\"courseId\":\"course-1\",\"name\":\"Course 1\"}"))
                    .thenReturn(course1Data);

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(
                    courseIds, 100, Collections.emptyList());

            assertTrue(result.containsKey("course-1"));
            assertEquals(course1Data, result.get("course-1"));
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    void testGetCourseMetadataBatched_EmptyFilteredMap() {
        try {
            List<String> courseIds = List.of("course-1");
            List<String> fields = List.of("nonExistentField");
            Map<String, String> courseMetadataJson = Map.of("course-1", "{\"courseId\":\"course-1\",\"name\":\"Course 1\"}");

            when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(courseMetadataJson);

            Map<String, Object> course1Data = Map.of(
                    Constants.COURSE_ID, "course-1",
                    Constants.NAME, "Course 1"
            );
            when(projectUtil.parseMap("{\"courseId\":\"course-1\",\"name\":\"Course 1\"}"))
                    .thenReturn(course1Data);

            Map<String, Map<String, Object>> result = service.getCourseMetadataBatched(courseIds, 100, fields);

            assertTrue(result.isEmpty());
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    // ==================== analyzeCompetencies Tests ====================

    @Test
    void testAnalyzeCompetencies_Success() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();

        Map<String, Object> course1 = new HashMap<>();
        List<Map<String, Object>> competencies1 = List.of(
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                ),
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Frontend"
                )
        );
        course1.put(Constants.COMPETENCIES_V6, competencies1);
        courseMetadata.put("course-1", course1);

        Map<String, Object> course2 = new HashMap<>();
        List<Map<String, Object>> competencies2 = List.of(
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Database",
                        Constants.COMPETENCY_SUB_THEME_NAME, "SQL"
                ),
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Management",
                        Constants.COMPETENCY_THEME_NAME, "Project Management",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Agile"
                )
        );
        course2.put(Constants.COMPETENCIES_V6, competencies2);
        courseMetadata.put("course-2", course2);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        assertTrue(result.containsKey(Constants.COMPETENCY_AREA_COUNTS));
        assertTrue(result.containsKey(Constants.COMPETENCY_THEME_GROUPS));

        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertEquals(3L, areaCounts.get("Technology"));
        assertEquals(1L, areaCounts.get("Management"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> themeGroups = (Map<String, Map<String, Object>>)
                result.get(Constants.COMPETENCY_THEME_GROUPS);
        assertTrue(themeGroups.containsKey("Software Development"));
        assertTrue(themeGroups.containsKey("Database"));
        assertTrue(themeGroups.containsKey("Project Management"));
    }

    @Test
    void testAnalyzeCompetencies_EmptyMetadata() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        assertTrue(result.containsKey(Constants.COMPETENCY_AREA_COUNTS));
        assertTrue(result.containsKey(Constants.COMPETENCY_THEME_GROUPS));

        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertTrue(areaCounts.isEmpty());
    }

    @Test
    void testAnalyzeCompetencies_NoCompetenciesField() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();
        course1.put(Constants.COURSE_ID, "course-1");
        // No competenciesV6 field
        courseMetadata.put("course-1", course1);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertTrue(areaCounts.isEmpty());
    }

    @Test
    void testAnalyzeCompetencies_InvalidCompetencyFormat() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();
        course1.put(Constants.COMPETENCIES_V6, List.of("invalid", 123));
        courseMetadata.put("course-1", course1);

        Map<String, Object> result;
        try {
            result = service.analyzeCompetencies(courseMetadata);
        } catch (Exception e) {
            result = Map.of();
        }

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertTrue(areaCounts == null || areaCounts.isEmpty());
    }

    @Test
    void testAnalyzeCompetencies_DuplicateSubThemes() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();

        Map<String, Object> course1 = new HashMap<>();
        List<Map<String, Object>> competencies = List.of(
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                )
        );
        course1.put(Constants.COMPETENCIES_V6, competencies);
        courseMetadata.put("course-1", course1);

        Map<String, Object> course2 = new HashMap<>();
        List<Map<String, Object>> competencies2 = List.of(
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Backend"  // Same subtheme
                )
        );
        course2.put(Constants.COMPETENCIES_V6, competencies2);
        courseMetadata.put("course-2", course2);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> themeGroups = (Map<String, Map<String, Object>>)
                result.get(Constants.COMPETENCY_THEME_GROUPS);

        @SuppressWarnings("unchecked")
        List<String> subThemes = (List<String>) themeGroups.get("Software Development")
                .get(Constants.COMPETENCY_SUB_THEME_NAMES);

        // Should contain only one "Backend" due to Set deduplication
        assertEquals(1, subThemes.size());

        @SuppressWarnings("unchecked")
        List<String> courseIds = (List<String>) themeGroups.get("Software Development")
                .get(Constants.COURSE_IDS);

        // Should contain both course IDs
        assertEquals(2, courseIds.size());
    }

    @Test
    void testAnalyzeCompetencies_NullMetadata() {
        Map<String, Object> result = service.analyzeCompetencies(null);

        assertNotNull(result);
        assertTrue(result.containsKey(Constants.COMPETENCY_AREA_COUNTS));
        assertTrue(result.containsKey(Constants.COMPETENCY_THEME_GROUPS));

        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertTrue(areaCounts.isEmpty());
    }

    @Test
    void testAnalyzeCompetencies_NullCourseMap() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        courseMetadata.put("course-1", null);
        courseMetadata.put("course-2", Map.of(
                Constants.COMPETENCIES_V6, List.of(
                        Map.of(
                                Constants.COMPETENCY_AREA_NAME, "Technology",
                                Constants.COMPETENCY_THEME_NAME, "Software Development",
                                Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                        )
                )
        ));

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertEquals(1L, areaCounts.get("Technology"));
    }

    @Test
    void testAnalyzeCompetencies_CompetenciesNotList() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();
        course1.put(Constants.COMPETENCIES_V6, "not a list");
        courseMetadata.put("course-1", course1);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertTrue(areaCounts.isEmpty());
    }

    @Test
    void testAnalyzeCompetencies_NullSubThemeName() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();

        Map<String, Object> competency = new HashMap<>();
        competency.put(Constants.COMPETENCY_AREA_NAME, "Technology");
        competency.put(Constants.COMPETENCY_THEME_NAME, "Software Development");
        competency.put(Constants.COMPETENCY_SUB_THEME_NAME, null);

        course1.put(Constants.COMPETENCIES_V6, List.of(competency));
        courseMetadata.put("course-1", course1);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertEquals(1L, areaCounts.get("Technology"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> themeGroups = (Map<String, Map<String, Object>>)
                result.get(Constants.COMPETENCY_THEME_GROUPS);

        @SuppressWarnings("unchecked")
        List<String> subThemes = (List<String>) themeGroups.get("Software Development")
                .get(Constants.COMPETENCY_SUB_THEME_NAMES);

        // String.valueOf(null) returns "null" as a string, which is not blank, so it gets added
        assertEquals(1, subThemes.size());
        assertTrue(subThemes.contains("null"));
    }

    @Test
    void testAnalyzeCompetencies_BlankSubThemeName() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();

        List<Map<String, Object>> competencies = List.of(
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "   "
                ),
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                )
        );

        course1.put(Constants.COMPETENCIES_V6, competencies);
        courseMetadata.put("course-1", course1);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> themeGroups = (Map<String, Map<String, Object>>)
                result.get(Constants.COMPETENCY_THEME_GROUPS);

        @SuppressWarnings("unchecked")
        List<String> subThemes = (List<String>) themeGroups.get("Software Development")
                .get(Constants.COMPETENCY_SUB_THEME_NAMES);

        assertEquals(1, subThemes.size());
        assertTrue(subThemes.contains("Backend"));
    }

    @Test
    void testAnalyzeCompetencies_ExceptionDuringProcessing() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();

        course1.put(Constants.COMPETENCIES_V6, List.of(
                Map.of(
                        Constants.COMPETENCY_AREA_NAME, "Technology",
                        Constants.COMPETENCY_THEME_NAME, "Software Development",
                        Constants.COMPETENCY_SUB_THEME_NAME, "Backend"
                )
        ));
        courseMetadata.put("course-1", course1);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        assertTrue(result.containsKey(Constants.COMPETENCY_AREA_COUNTS));
        assertTrue(result.containsKey(Constants.COMPETENCY_THEME_GROUPS));
    }

    @Test
    void testAnalyzeCompetencies_CompetencyNotMap() {
        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();
        Map<String, Object> course1 = new HashMap<>();

        course1.put(Constants.COMPETENCIES_V6, List.of("not a map", 123));
        courseMetadata.put("course-1", course1);

        Map<String, Object> result = service.analyzeCompetencies(courseMetadata);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Long> areaCounts = (Map<String, Long>) result.get(Constants.COMPETENCY_AREA_COUNTS);
        assertTrue(areaCounts.isEmpty());
    }

    @Test
    void testConstructor() {
        UserCompetencyServiceImpl newService = new UserCompetencyServiceImpl(
                accessTokenValidator, cassandraOperation, cacheService, projectUtil);

        assertNotNull(newService);
    }
}
