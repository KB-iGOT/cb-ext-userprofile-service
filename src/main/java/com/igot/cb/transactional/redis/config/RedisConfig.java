package com.igot.cb.transactional.redis.config;

import com.igot.cb.util.CbServerProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@EnableCaching
public class RedisConfig {

    CbServerProperties cbProperties;

    public RedisConfig(CbServerProperties cbProperties) {
        this.cbProperties = cbProperties;
    }

    @Bean
    public JedisPool jedisPool() {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        return new JedisPool(poolConfig, cbProperties.getRedisHostName(),
                Integer.parseInt(cbProperties.getRedisPort()));
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
        poolConfig.setNumTestsPerEvictionRun(cbProperties.getRedisNumTestsPerEvictionRun());
        poolConfig.setBlockWhenExhausted(cbProperties.getRedisBlockWhenExhausted());
        return poolConfig;
    }
}
