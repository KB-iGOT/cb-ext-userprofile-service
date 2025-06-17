package com.igot.cb.transactional.redis.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private JedisPool jedisDataPopulationPool;

    @Mock
    private Jedis jedis;

    @Mock
    private CbServerProperties serverProperties;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(jedisPool.getResource()).thenReturn(jedis);
        lenient().when(jedisDataPopulationPool.getResource()).thenReturn(jedis);
    }

    @Test
    void hget_ReturnsValueAndResetsTTL_WhenFieldExists() {
        when(jedis.hmget("key", "field")).thenReturn(List.of("value"));
        String result = cacheService.hget("key", 0, "field", 100);
        assertEquals("value", result);
        verify(jedis).expire("key", 100);
    }

    @Test
    void hget_ReturnsNull_WhenFieldDoesNotExist() {
        lenient().when(jedis.hmget("key", "field")).thenReturn(Arrays.asList((String) null));
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
    }

    @Test
    void hset_SetsValueAndTTL() {
        cacheService.hset("key", 0, "field", "value");
        verify(jedis).hset("key", "field", "value");
        verify(jedis).expire("key", 84600);
    }

    @Test
    void putCache_SerializesAndSetsValueWithTTL() throws Exception {
        Object obj = Map.of("a", 1);
        cacheService.putCache("key", obj, 123);
        verify(jedis).set(eq("key"), anyString());
        verify(jedis).expire("key", 123);
    }

    @Test
    void putCache_UsesDefaultTTL() throws Exception {
        Object obj = Map.of("a", 1);
        cacheService.putCache("key", obj);
        verify(jedis).set(eq("key"), anyString());
        verify(jedis).expire("key", 84600);
    }

    @Test
    void getCache_ReturnsValue_WhenKeyExists() {
        when(jedis.get("key")).thenReturn("value");
        String result = cacheService.getCache("key");
        assertEquals("value", result);
    }

    @Test
    void getCache_ReturnsNull_OnException() {
        when(jedis.get("key")).thenThrow(new RuntimeException("fail"));
        String result = cacheService.getCache("key");
        assertNull(result);
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsMap_WhenAllKeysHaveValues() {
        when(jedis.mget("k1", "k2")).thenReturn(List.of("{\"a\":1}", "{\"b\":2}"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertEquals(2, result.size());
        assertEquals("{\"a\":1}", result.get("k1"));
        assertEquals("{\"b\":2}", result.get("k2"));
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenInputListIsNull() {
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenInputListIsEmpty() {
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenValuesListIsNull() {
        when(jedis.mget("k1")).thenReturn(null);
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1"));
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenKeysAndValuesSizeMismatch() {
        when(jedis.mget("k1", "k2")).thenReturn(List.of("{\"a\":1}"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertTrue(result.isEmpty());
    }

    @Test
    void hget_ReturnsNull_WhenFieldListIsEmpty() {
        when(jedis.hmget("key", "field")).thenReturn(List.of());
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
    }

    @Test
    void hget_ReturnsNull_OnException() {
        when(jedis.hmget("key", "field")).thenThrow(new RuntimeException("fail"));
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
    }

    @Test
    void hset_DoesNotThrow_OnException() {
        doThrow(new RuntimeException("fail")).when(jedis).hset("key", "field", "value");
        cacheService.hset("key", 0, "field", "value");
    }

    @Test
    void putCache_DoesNotThrow_OnException() throws Exception {
        doThrow(new RuntimeException("fail")).when(jedis).set(eq("key"), anyString());
        cacheService.putCache("key", Map.of("a", 1), 100);
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_OnException() {
        when(jedis.mget(any(String[].class))).thenThrow(new RuntimeException("fail"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertTrue(result.isEmpty());
    }

    @Test
    void hget_ReturnsNull_WhenResultIsEmpty() {
        when(jedis.hmget("key", "field")).thenReturn(List.of());
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
        verify(jedis, never()).expire(anyString(), anyInt());
    }

    @Test
    void hget_ReturnsNull_WhenResultIsNull() {
        when(jedis.hmget("key", "field")).thenReturn(null);
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
        verify(jedis, never()).expire(anyString(), anyInt());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenValuesListIsEmpty() {
        when(jedis.mget("k1")).thenReturn(List.of());
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1"));
        assertTrue(result.isEmpty());
    }
}