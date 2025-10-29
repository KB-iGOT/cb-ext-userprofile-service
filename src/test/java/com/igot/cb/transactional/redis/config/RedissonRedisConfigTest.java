package com.igot.cb.transactional.redis.config;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonRedisConfigTest {

    private CbServerProperties cbServerProperties;
    private RedissonRedisConfig redissonRedisConfig;

    @BeforeEach
    void setUp() {
        cbServerProperties = mock(CbServerProperties.class);
        lenient().when(cbServerProperties.getRedisHostName()).thenReturn("localhost");
        lenient().when(cbServerProperties.getRedisPort()).thenReturn("6379");
        lenient().when(cbServerProperties.getRedisDataHostName()).thenReturn("localhost");
        lenient().when(cbServerProperties.getRedisDataPort()).thenReturn("6380");
        lenient().when(cbServerProperties.getRedisMaxTotal()).thenReturn(10);
        lenient().when(cbServerProperties.getRedisMinIdle()).thenReturn(2);
        redissonRedisConfig = new RedissonRedisConfig(cbServerProperties);
    }


    @Test
    void testConstructor() {
        RedissonRedisConfig config = new RedissonRedisConfig(cbServerProperties);
        assertThat(config).isNotNull();
    }

    @Test
    void testRedissonClient() {
        RedissonClient mockClient = mock(RedissonClient.class);
        try (MockedStatic<Redisson> redissonStatic = Mockito.mockStatic(Redisson.class)) {
            redissonStatic.when(() -> Redisson.create(any(Config.class))).thenReturn(mockClient);
            RedissonClient result = redissonRedisConfig.redissonClient();
            assertThat(result).isEqualTo(mockClient);
            redissonStatic.verify(() -> Redisson.create(any(Config.class)), times(1));
        }
    }

    @Test
    void testRedissonDataPopulationClient() {
        RedissonClient mockClient = mock(RedissonClient.class);
        try (MockedStatic<Redisson> redissonStatic = Mockito.mockStatic(Redisson.class)) {
            redissonStatic.when(() -> Redisson.create(any(Config.class))).thenReturn(mockClient);
            RedissonClient result = redissonRedisConfig.redissonDataPopulationClient();
            assertThat(result).isEqualTo(mockClient);
            redissonStatic.verify(() -> Redisson.create(any(Config.class)), times(1));
        }
    }
}
