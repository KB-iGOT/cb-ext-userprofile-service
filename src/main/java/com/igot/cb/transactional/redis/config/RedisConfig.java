package com.igot.cb.transactional.redis.config;

import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    @Autowired
    CbServerProperties cbProperties;

    @Bean
    public JedisPool jedisPool() {
        return buildPool(cbProperties.getRedisHostName(),
                Integer.parseInt(cbProperties.getRedisPort()),
                cbProperties.isRedisPasswordRequired(),
                cbProperties.getRedisUsername(),
                cbProperties.getRedisPassword());
    }

    @Bean
    public JedisPool jedisDataPopulationPool() {
        return buildPool(cbProperties.getRedisDataHostName(),
                Integer.parseInt(cbProperties.getRedisDataPort()),
                cbProperties.isRedisDataPasswordRequired(),
                cbProperties.getRedisDataUsername(),
                cbProperties.getRedisDataPassword());
    }

    /**
     * Builds one pool, authenticated or not, for whichever of the two Redis servers the arguments
     * describe. The two servers are configured independently, so one may require credentials while
     * the other does not.
     */
    private JedisPool buildPool(String host, int port, boolean passwordRequired, String username, String password) {
        final JedisPoolConfig poolConfig = buildPoolConfig();

        if (!passwordRequired) {
            log.warn("Redis pool for {}:{} created WITHOUT authentication - if that server has requirepass set, every operation will fail with NOAUTH and be swallowed as a cache miss", host, port);
            return new JedisPool(poolConfig, host, port);
        }
        requireCredentials(host, port, username, password);
        // The username is safe to log and is what makes an ACL misconfiguration diagnosable from the
        // startup line alone; the password must never appear here.
        log.info("Redis pool for {}:{} created with authentication enabled for user '{}'", host, port, username);
        return new JedisPool(poolConfig, host, port, username, password);
    }

    /**
     * Fails at bean creation rather than on the first Redis call. An unset property reads as "" rather
     * than null, and Jedis sends the two-argument AUTH whenever the username is non-null - so without
     * this guard the server answers WRONGPASS on every command and CacheService swallows it as a miss.
     *
     * <p>Shared by {@link #buildPool} and {@link #searchResultRedisTemplate()}, which reach the same
     * cache instance by two different routes.
     */
    private void requireCredentials(String host, int port, String username, String password) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalStateException("A username is required for the Redis instance at " + host + ":" + port + " but not configured");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalStateException("A password is required for the Redis instance at " + host + ":" + port + " but not configured");
        }
    }

    private JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxIdle(cbProperties.getRedisMaxIdle());
        poolConfig.setMaxTotal(cbProperties.getRedisMaxTotal());
        poolConfig.setMinIdle(cbProperties.getRedisMinIdle());
        poolConfig.setTestOnBorrow(cbProperties.getRedisTestOnBorrow());
        poolConfig.setTestOnReturn(cbProperties.getRedisTestOnReturn());
        poolConfig.setTestWhileIdle(cbProperties.getRedisTestWhileIdle());
        poolConfig.setMinEvictableIdleTimeMillis(cbProperties.getRedisMinEvictableIdleTimeMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(cbProperties.getRedisNumTestsPerEvictionRun());
        poolConfig.setNumTestsPerEvictionRun(cbProperties.getRedisNumTestsPerEvictionRun());
        poolConfig.setBlockWhenExhausted(cbProperties.getRedisBlockWhenExhausted());
        return poolConfig;
    }

    @Bean(name = Constants.SEARCH_RESULT_REDIS_TEMPLATE)
    public RedisTemplate<String, SearchResult> searchResultRedisTemplate() {
        org.springframework.data.redis.connection.jedis.JedisConnectionFactory jedisConnectionFactory = new org.springframework.data.redis.connection.jedis.JedisConnectionFactory();
        jedisConnectionFactory.setHostName(cbProperties.getRedisHostName());
        jedisConnectionFactory.setPort(Integer.parseInt(cbProperties.getRedisPort()));
        // This template builds its own connection factory and so does NOT inherit the credentials
        // applied to jedisPool, even though it points at the same cache instance. Without this the
        // pools would authenticate and this bean alone would fail with NOAUTH. It reaches the cache
        // server, so it takes the redis.* credentials, never redis.data.*.
        if (cbProperties.isRedisPasswordRequired()) {
            requireCredentials(cbProperties.getRedisHostName(), Integer.parseInt(cbProperties.getRedisPort()),
                    cbProperties.getRedisUsername(), cbProperties.getRedisPassword());
            // JedisConnectionFactory exposes setPassword but has no setUsername, so the username has
            // to go through the standalone configuration. Both must be set before afterPropertiesSet.
            jedisConnectionFactory.getStandaloneConfiguration().setUsername(cbProperties.getRedisUsername());
            jedisConnectionFactory.setPassword(cbProperties.getRedisPassword());
        }
        jedisConnectionFactory.afterPropertiesSet();
        RedisTemplate<String, SearchResult> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory);
        template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
        template.setValueSerializer(new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>(SearchResult.class));
        template.afterPropertiesSet();
        return template;
    }
}
