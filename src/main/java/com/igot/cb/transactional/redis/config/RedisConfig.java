package com.igot.cb.transactional.redis.config;

import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
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
        final JedisPoolConfig poolConfig = buildPoolConfig();
        return createJedisPool(poolConfig, cbProperties.getRedisHostName(), cbProperties.getRedisPort(),
                cbProperties.getRedisPassword());
    }

    @Bean
    public JedisPool jedisDataPopulationPool() {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        return createJedisPool(poolConfig, cbProperties.getRedisDataHostName(), cbProperties.getRedisDataPort(),
                cbProperties.getRedisDataPassword());
    }

    private JedisPool createJedisPool(JedisPoolConfig poolConfig, String host, String port, String password) {
        int redisPort = Integer.parseInt(port);
        if (StringUtils.hasText(password)) {
            log.info("Redis server {}:{} is configured with password authentication", host, redisPort);
            return new JedisPool(poolConfig, host, redisPort, null, password);
        }
        log.warn("Redis server {}:{} is configured without password authentication", host, redisPort);
        return new JedisPool(poolConfig, host, redisPort);
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
        if (StringUtils.hasText(cbProperties.getRedisPassword())) {
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
