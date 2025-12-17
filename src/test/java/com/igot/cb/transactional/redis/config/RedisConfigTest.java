package com.igot.cb.transactional.redis.config;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private CbServerProperties cbProperties;

    @Test
    void jedisPool_ReturnsConfiguredJedisPool() {
        lenient().when(cbProperties.getRedisHostName()).thenReturn("localhost");
        lenient().when(cbProperties.getRedisPort()).thenReturn("6379");
        lenient().when(cbProperties.getRedisMaxIdle()).thenReturn(5);
        lenient().when(cbProperties.getRedisMaxTotal()).thenReturn(10);
        lenient().when(cbProperties.getRedisMinIdle()).thenReturn(1);
        lenient().when(cbProperties.getRedisTestOnBorrow()).thenReturn(true);
        lenient().when(cbProperties.getRedisTestOnReturn()).thenReturn(false);
        lenient().when(cbProperties.getRedisTestWhileIdle()).thenReturn(true);
        lenient().when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(60000L);
        lenient().when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(3);
        lenient().when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(true);

        RedisConfig redisConfig = new RedisConfig(cbProperties);
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        JedisPool pool = redisConfig.jedisPool();
        assertNotNull(pool);
    }

    @Test
    void jedisDataPopulationPool_ReturnsConfiguredJedisPool() {
        lenient().when(cbProperties.getRedisDataHostName()).thenReturn("localhost");
        lenient().when(cbProperties.getRedisDataPort()).thenReturn("6380");
        lenient().when(cbProperties.getRedisMaxIdle()).thenReturn(2);
        lenient().when(cbProperties.getRedisMaxTotal()).thenReturn(4);
        lenient().when(cbProperties.getRedisMinIdle()).thenReturn(1);
        lenient().when(cbProperties.getRedisTestOnBorrow()).thenReturn(false);
        lenient().when(cbProperties.getRedisTestOnReturn()).thenReturn(true);
        lenient().when(cbProperties.getRedisTestWhileIdle()).thenReturn(false);
        lenient().when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(120000L);
        lenient().when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(2);
        lenient().when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(false);

        RedisConfig redisConfig = new RedisConfig(cbProperties);
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        JedisPool pool = redisConfig.jedisDataPopulationPool();
        assertNotNull(pool);
    }
}