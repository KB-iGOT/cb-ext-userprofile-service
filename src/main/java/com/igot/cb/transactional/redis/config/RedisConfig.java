package com.igot.cb.transactional.redis.config;

import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@EnableCaching
public class RedisConfig {

    @Autowired
    CbServerProperties cbProperties;

    @Bean
    public JedisPool jedisPool() {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        JedisPool jedisPool = new JedisPool(poolConfig, cbProperties.getRedisHostName(),
                Integer.parseInt(cbProperties.getRedisPort()));
        return jedisPool;
    }

    /**
     * Pool for the per-user cache hash ({@code userprofile:{userId}}) owned by sunbird-lms-service.
     * Returns {@code null} (bean absent) when {@code user.profile.redis.host} is blank, so callers
     * must treat the pool as optional.
     */
    @Bean(name = "userProfileJedisPool")
    public JedisPool userProfileJedisPool() {
        String host = cbProperties.getUserProfileRedisHost();
        if (host == null || host.trim().isEmpty()) {
            return null;
        }
        final JedisPoolConfig poolConfig = buildPoolConfig();
        int port = cbProperties.getUserProfileRedisPort();
        int index = cbProperties.getUserProfileRedisIndex();
        int timeout = 2000;
        if (cbProperties.isUserProfileRedisPasswordEnabled()) {
            String user = cbProperties.getUserProfileRedisUsername();
            String password = cbProperties.getUserProfileRedisPassword();
            return (user == null || user.trim().isEmpty())
                    ? new JedisPool(poolConfig, host.trim(), port, timeout, password, index)
                    : new JedisPool(poolConfig, host.trim(), port, timeout, user.trim(), password, index);
        }
        return new JedisPool(poolConfig, host.trim(), port, timeout, null, index);
    }

    @Bean
    public JedisPool jedisDataPopulationPool() {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        return new JedisPool(poolConfig, cbProperties.getRedisDataHostName(),
                Integer.parseInt(cbProperties.getRedisDataPort()));
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
        jedisConnectionFactory.afterPropertiesSet();
        RedisTemplate<String, SearchResult> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory);
        template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
        template.setValueSerializer(new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>(SearchResult.class));
        template.afterPropertiesSet();
        return template;
    }
}
