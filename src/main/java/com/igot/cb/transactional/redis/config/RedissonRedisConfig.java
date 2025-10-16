package com.igot.cb.transactional.redis.config;

import com.igot.cb.util.CbServerProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RedissonRedisConfig {

    private final CbServerProperties cbProperties;

    public RedissonRedisConfig(CbServerProperties cbProperties) {
        this.cbProperties = cbProperties;
    }

    @Bean(destroyMethod = "shutdown", name = "redissonClient")
    @Primary
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + cbProperties.getRedisHostName() + ":" + cbProperties.getRedisPort())
                .setConnectionPoolSize(cbProperties.getRedisMaxTotal())
                .setConnectionMinimumIdleSize(cbProperties.getRedisMinIdle())
                .setIdleConnectionTimeout(cbProperties.getIdleConnectionTimeout())
                .setConnectTimeout(cbProperties.getConnectTimeout())
                .setTimeout(cbProperties.getTimeout())
                .setRetryAttempts(cbProperties.getRetryAttempts())
                .setRetryInterval(cbProperties.getRetryInterval())
                .setPingConnectionInterval(cbProperties.getPingConnectionInterval())
                .setKeepAlive(cbProperties.isKeepAlive())
                .setTcpNoDelay(cbProperties.isTcpNoDelay());

        return Redisson.create(config);
    }

    @Bean(destroyMethod = "shutdown", name = "redissonDataPopulationClient")
    public RedissonClient redissonDataPopulationClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + cbProperties.getRedisDataHostName() + ":" + cbProperties.getRedisDataPort())
                .setConnectionPoolSize(cbProperties.getRedisMaxTotal())
                .setConnectionMinimumIdleSize(cbProperties.getRedisMinIdle())
                .setIdleConnectionTimeout(cbProperties.getIdleConnectionTimeout())
                .setConnectTimeout(cbProperties.getConnectTimeout())
                .setTimeout(cbProperties.getTimeout())
                .setRetryAttempts(cbProperties.getRetryAttempts())
                .setRetryInterval(cbProperties.getRetryInterval())
                .setPingConnectionInterval(cbProperties.getPingConnectionInterval())
                .setKeepAlive(cbProperties.isKeepAlive())
                .setTcpNoDelay(cbProperties.isTcpNoDelay());

        return Redisson.create(config);
    }
}
