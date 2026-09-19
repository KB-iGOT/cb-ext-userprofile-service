package com.igot.cb.transactional.redis.config;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private CbServerProperties cbProperties;

    @Test
    void jedisPool_ReturnsConfiguredJedisPool() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
        when(cbProperties.getRedisMaxIdle()).thenReturn(5);
        when(cbProperties.getRedisMaxTotal()).thenReturn(10);
        when(cbProperties.getRedisMinIdle()).thenReturn(1);
        when(cbProperties.getRedisTestOnBorrow()).thenReturn(true);
        when(cbProperties.getRedisTestOnReturn()).thenReturn(false);
        when(cbProperties.getRedisTestWhileIdle()).thenReturn(true);
        when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(60000L);
        when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(3);
        when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(true);

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        JedisPool pool = redisConfig.jedisPool();
        assertNotNull(pool);
    }

    @Test
    void jedisDataPopulationPool_ReturnsConfiguredJedisPool() {
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

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        JedisPool pool = redisConfig.jedisDataPopulationPool();
        assertNotNull(pool);
    }

    /**
     * With the flag on, a missing username must fail at bean creation rather than on the first Redis
     * call. An unset property reads as "" rather than null, and Jedis sends the two-argument AUTH
     * whenever the username is non-null - so without this guard the server answers WRONGPASS on every
     * command and CacheService swallows it as a miss.
     */
    @Test
    void jedisPool_FailsWhenUsernameRequiredButMissing() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
        when(cbProperties.isRedisPasswordRequired()).thenReturn(true);
        when(cbProperties.getRedisUsername()).thenReturn("");
        when(cbProperties.getRedisPassword()).thenReturn("cache-secret");

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, redisConfig::jedisPool);
        assertTrue(ex.getMessage().contains("username"), ex.getMessage());
        // the message must point at the cache instance, not the data one
        assertTrue(ex.getMessage().contains("localhost:6379"), ex.getMessage());
    }

    @Test
    void jedisPool_FailsWhenPasswordRequiredButMissing() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
        when(cbProperties.isRedisPasswordRequired()).thenReturn(true);
        when(cbProperties.getRedisUsername()).thenReturn("cache-user");
        when(cbProperties.getRedisPassword()).thenReturn("  ");

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, redisConfig::jedisPool);
        assertTrue(ex.getMessage().contains("password"), ex.getMessage());
        assertTrue(ex.getMessage().contains("localhost:6379"), ex.getMessage());
    }

    /**
     * An absent key reads as null rather than "". Both spellings of "not configured" are rejected.
     */
    @Test
    void jedisPool_FailsWhenUsernameIsNull() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
        when(cbProperties.isRedisPasswordRequired()).thenReturn(true);
        when(cbProperties.getRedisUsername()).thenReturn(null);
        when(cbProperties.getRedisPassword()).thenReturn("cache-secret");

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, redisConfig::jedisPool);
        assertTrue(ex.getMessage().contains("username"), ex.getMessage());
    }

    /** The same two guards on the data pool, which is configured independently. */
    @Test
    void jedisDataPopulationPool_FailsWhenUsernameRequiredButMissing() {
        when(cbProperties.getRedisDataHostName()).thenReturn("localhost");
        when(cbProperties.getRedisDataPort()).thenReturn("6380");
        when(cbProperties.isRedisDataPasswordRequired()).thenReturn(true);
        when(cbProperties.getRedisDataUsername()).thenReturn("");
        when(cbProperties.getRedisDataPassword()).thenReturn("data-secret");

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, redisConfig::jedisDataPopulationPool);
        assertTrue(ex.getMessage().contains("username"), ex.getMessage());
        // and at the data instance - the port is what tells the two apart
        assertTrue(ex.getMessage().contains("localhost:6380"), ex.getMessage());
    }

    @Test
    void jedisDataPopulationPool_FailsWhenPasswordRequiredButMissing() {
        when(cbProperties.getRedisDataHostName()).thenReturn("localhost");
        when(cbProperties.getRedisDataPort()).thenReturn("6380");
        when(cbProperties.isRedisDataPasswordRequired()).thenReturn(true);
        when(cbProperties.getRedisDataUsername()).thenReturn("data-user");
        when(cbProperties.getRedisDataPassword()).thenReturn("  ");

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, redisConfig::jedisDataPopulationPool);
        assertTrue(ex.getMessage().contains("password"), ex.getMessage());
        assertTrue(ex.getMessage().contains("localhost:6380"), ex.getMessage());
    }

    /**
     * searchResultRedisTemplate builds its own connection factory and so does not inherit the pool's
     * credentials. It must trip the same guard, or flipping the flag on yields a service where the
     * pools authenticate and this one bean fails with NOAUTH.
     */
    @Test
    void searchResultRedisTemplate_FailsWhenCredentialsRequiredButMissing() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
        when(cbProperties.isRedisPasswordRequired()).thenReturn(true);
        when(cbProperties.getRedisUsername()).thenReturn("");
        when(cbProperties.getRedisPassword()).thenReturn("cache-secret");

        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "cbProperties", cbProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, redisConfig::searchResultRedisTemplate);
        assertTrue(ex.getMessage().contains("username"), ex.getMessage());
        assertTrue(ex.getMessage().contains("localhost:6379"), ex.getMessage());
    }
}