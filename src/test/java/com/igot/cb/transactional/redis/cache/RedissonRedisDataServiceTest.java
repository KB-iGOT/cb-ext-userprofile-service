package com.igot.cb.transactional.redis.cache;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedissonRedisDataServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private RKeys rKeys;

    @Mock
    private RMap<String, Object> rMap;

    @Mock
    private RList<String> rList;

    @Mock
    private RList<Map<String, Object>> rMapList;

    @InjectMocks
    private RedissonRedisDataService redisDataService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(cbServerProperties.getUserProfileKeysTtl()).thenReturn(5000);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testPutMap_WithValidData_ShouldStoreInRedis() {
        String redisKey = "user:map";
        Map<String, Object> data = Map.of("key1", "value1");
        when(rKeys.getType(redisKey)).thenReturn(RType.MAP);
        when(redissonClient.<String, Object>getMap(redisKey)).thenReturn(rMap);
        redisDataService.putMap(redisKey, data);
        verify(rMap).putAll(data);
        verify(rKeys).expire(redisKey, 5000L, TimeUnit.SECONDS);
    }

    @Test
    void testPutMap_WithEmptyData_ShouldDoNothing() {
        redisDataService.putMap("testKey", Collections.emptyMap());
        verifyNoInteractions(rKeys, rMap);
    }

    @Test
    void testPutMap_WithWrongType_ShouldDeleteAndRecreate() {
        String redisKey = "user:map";
        Map<String, Object> data = Map.of("a", "b");
        when(rKeys.getType(redisKey)).thenReturn(RType.LIST);
        when(redissonClient.<String, Object>getMap(redisKey)).thenReturn(rMap);
        redisDataService.putMap(redisKey, data);
        verify(rKeys).delete(redisKey);
        verify(rMap).putAll(data);
        verify(rKeys).expire(redisKey, 5000L, TimeUnit.SECONDS);
    }


    @Test
    void testGetMap_ShouldReturnMapFromRedis() {
        String redisKey = "user:map";
        Map<String, Object> mockMap = Map.of("k1", "v1");
        when(redissonClient.<String, Object>getMap(redisKey)).thenReturn(rMap);
        when(rMap.readAllMap()).thenReturn(mockMap);
        Map<String, Object> result = redisDataService.getMap(redisKey);
        assertEquals(mockMap, result);
        verify(rMap).readAllMap();
    }

    @Test
    void testPutStringList_WithValidList_ShouldStoreInRedis() {
        String redisKey = "list:key";
        List<String> list = List.of("a", "b");
        when(rKeys.getType(redisKey)).thenReturn(RType.LIST);
        when(redissonClient.<String>getList(redisKey)).thenReturn(rList);
        redisDataService.putStringList(redisKey, list);
        verify(rList).clear();
        verify(rList).addAll(list);
        verify(rKeys).expire(redisKey, 5000L, TimeUnit.SECONDS);
    }

    @Test
    void testPutStringList_WithEmptyList_ShouldDoNothing() {
        redisDataService.putStringList("list:key", Collections.emptyList());
        verifyNoInteractions(rKeys, rList);
    }

    @Test
    void testPutStringList_WithWrongType_ShouldDeleteAndRecreate() {
        String redisKey = "list:key";
        List<String> list = List.of("x", "y");
        when(rKeys.getType(redisKey)).thenReturn(RType.MAP);
        when(redissonClient.<String>getList(redisKey)).thenReturn(rList);
        redisDataService.putStringList(redisKey, list);
        verify(rKeys).delete(redisKey);
        verify(rList).clear();
        verify(rList).addAll(list);
        verify(rKeys).expire(redisKey, 5000L, TimeUnit.SECONDS);
    }

    @Test
    void testGetStringList_ShouldReturnListFromRedis() {
        String redisKey = "list:key";
        List<String> mockList = List.of("a", "b");
        when(redissonClient.<String>getList(redisKey)).thenReturn(rList);
        when(rList.isEmpty()).thenReturn(false);
        when(rList.readAll()).thenReturn(mockList);
        List<String> result = redisDataService.getStringList(redisKey);
        assertEquals(mockList, result);
    }

    @Test
    void testGetStringList_WhenEmpty_ShouldReturnEmptyList() {
        when(redissonClient.<String>getList("list:key")).thenReturn(rList);
        when(rList.isEmpty()).thenReturn(true);
        List<String> result = redisDataService.getStringList("list:key");
        assertTrue(result.isEmpty());
    }


    @Test
    void testPutMapList_WithValidData_ShouldStoreInRedis() {
        String redisKey = "maplist:key";
        List<Map<String, Object>> list = List.of(Map.of("id", 1, "name", "John"));

        when(rKeys.getType(redisKey)).thenReturn(RType.LIST);
        when(redissonClient.<Map<String, Object>>getList(redisKey)).thenReturn(rMapList);

        redisDataService.putMapList(redisKey, list);

        verify(rMapList).clear();
        verify(rMapList).addAll(list);
        verify(rKeys).expire(redisKey, 5000L, TimeUnit.SECONDS);
    }

    @Test
    void testPutMapList_WithEmptyList_ShouldDoNothing() {
        redisDataService.putMapList("maplist:key", Collections.emptyList());
        verifyNoInteractions(rKeys, rMapList);
    }

    @Test
    void testPutMapList_WithWrongType_ShouldDeleteAndRecreate() {
        String redisKey = "maplist:key";
        List<Map<String, Object>> list = List.of(Map.of("foo", "bar"));

        when(rKeys.getType(redisKey)).thenReturn(RType.MAP);
        when(redissonClient.<Map<String, Object>>getList(redisKey)).thenReturn(rMapList);

        redisDataService.putMapList(redisKey, list);

        verify(rKeys).delete(redisKey);
        verify(rMapList).clear();
        verify(rMapList).addAll(list);
        verify(rKeys).expire(redisKey, 5000L, TimeUnit.SECONDS);
    }

    @Test
    void testGetMapList_ShouldReturnListFromRedis() {
        String redisKey = "maplist:key";
        List<Map<String, Object>> expected = List.of(Map.of("id", 1));

        when(redissonClient.<Map<String, Object>>getList(redisKey)).thenReturn(rMapList);
        when(rMapList.isEmpty()).thenReturn(false);
        when(rMapList.readAll()).thenReturn(expected);

        List<Map<String, Object>> result = redisDataService.getMapList(redisKey);

        assertEquals(expected, result);
        verify(rMapList).readAll();
    }

    @Test
    void testGetMapList_WhenEmpty_ShouldReturnEmptyList() {
        when(redissonClient.<Map<String, Object>>getList("maplist:key")).thenReturn(rMapList);
        when(rMapList.isEmpty()).thenReturn(true);

        List<Map<String, Object>> result = redisDataService.getMapList("maplist:key");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetMapList_WhenNull_ShouldReturnEmptyList() {
        when(redissonClient.<Map<String, Object>>getList("maplist:key")).thenReturn(null);
        List<Map<String, Object>> result = redisDataService.getMapList("maplist:key");
        assertTrue(result.isEmpty());
    }
}
