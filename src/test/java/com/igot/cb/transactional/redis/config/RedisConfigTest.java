package com.igot.cb.transactional.redis.config;

import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private CbServerProperties cbProperties;

    @Test
    void jedisPool_ConfiguresPasswordWhenProvided() {
        configurePrimaryRedis();
        when(cbProperties.getRedisPassword()).thenReturn("primary-secret");

        JedisPool pool = redisConfig().jedisPool();
        try {
            assertNotNull(pool);
            assertEquals("primary-secret", getPassword(pool));
        } finally {
            pool.close();
        }
    }

    @Test
    void jedisPool_DoesNotConfigurePasswordWhenBlank() {
        configurePrimaryRedis();
        when(cbProperties.getRedisPassword()).thenReturn("   ");

        JedisPool pool = redisConfig().jedisPool();
        try {
            assertNull(getPassword(pool));
        } finally {
            pool.close();
        }
    }

    @Test
    void jedisDataPopulationPool_ConfiguresItsOwnPasswordWhenProvided() {
        configureDataRedis();
        when(cbProperties.getRedisDataPassword()).thenReturn("data-secret");

        JedisPool pool = redisConfig().jedisDataPopulationPool();
        try {
            assertNotNull(pool);
            assertEquals("data-secret", getPassword(pool));
        } finally {
            pool.close();
        }
    }

    @Test
    void jedisDataPopulationPool_DoesNotConfigurePasswordWhenMissing() {
        configureDataRedis();

        JedisPool pool = redisConfig().jedisDataPopulationPool();
        try {
            assertNull(getPassword(pool));
        } finally {
            pool.close();
        }
    }

    @Test
    void searchResultRedisTemplate_ConfiguresPrimaryPasswordWhenProvided() {
        configurePrimaryRedisEndpoint();
        when(cbProperties.getRedisPassword()).thenReturn("primary-secret");

        RedisTemplate<String, SearchResult> template = redisConfig().searchResultRedisTemplate();
        JedisConnectionFactory connectionFactory = (JedisConnectionFactory) template.getConnectionFactory();

        assertNotNull(connectionFactory);
        assertEquals("primary-secret", connectionFactory.getPassword());
        connectionFactory.destroy();
    }

    @Test
    void searchResultRedisTemplate_DoesNotConfigurePasswordWhenMissing() {
        configurePrimaryRedisEndpoint();

        RedisTemplate<String, SearchResult> template = redisConfig().searchResultRedisTemplate();
        JedisConnectionFactory connectionFactory = (JedisConnectionFactory) template.getConnectionFactory();

        assertNotNull(connectionFactory);
        assertNull(connectionFactory.getPassword());
        connectionFactory.destroy();
    }

    private void configurePrimaryRedis() {
        configurePrimaryRedisEndpoint();
        when(cbProperties.getRedisMaxIdle()).thenReturn(5);
        when(cbProperties.getRedisMaxTotal()).thenReturn(10);
        when(cbProperties.getRedisMinIdle()).thenReturn(1);
        when(cbProperties.getRedisTestOnBorrow()).thenReturn(true);
        when(cbProperties.getRedisTestOnReturn()).thenReturn(false);
        when(cbProperties.getRedisTestWhileIdle()).thenReturn(true);
        when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(60000L);
        when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(3);
        when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(true);
    }

    private void configurePrimaryRedisEndpoint() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
    }

    private void configureDataRedis() {
        when(cbProperties.getRedisDataHostName()).thenReturn("localhost");
        when(cbProperties.getRedisDataPort()).thenReturn("6380");
        when(cbProperties.getRedisMaxIdle()).thenReturn(2);
        when(cbProperties.getRedisMaxTotal()).thenReturn(4);
        when(cbProperties.getRedisMinIdle()).thenReturn(1);
        when(cbProperties.getRedisTestOnBorrow()).thenReturn(false);
        when(cbProperties.getRedisTestOnReturn()).thenReturn(true);
        when(cbProperties.getRedisTestWhileIdle()).thenReturn(false);
        when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(120000L);
        when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(2);
        when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(false);
    }

    private RedisConfig redisConfig() {
        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);
        return redisConfig;
    }

    private String getPassword(JedisPool pool) {
        Object clientConfig = ReflectionTestUtils.getField(pool.getFactory(), "clientConfig");
        return ((JedisClientConfig) clientConfig).getPassword();
    }
}
