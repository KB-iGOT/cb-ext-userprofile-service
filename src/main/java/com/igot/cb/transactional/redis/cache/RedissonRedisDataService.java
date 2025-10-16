package com.igot.cb.transactional.redis.cache;

import com.igot.cb.util.CbServerProperties;
import org.redisson.api.RList;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.redisson.api.RMap;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedissonRedisDataService {

    private final RedissonClient redissonClient;

    private final CbServerProperties cbServerProperties;

    public RedissonRedisDataService(RedissonClient redissonClient, CbServerProperties cbServerProperties) {
        this.redissonClient = redissonClient;
        this.cbServerProperties = cbServerProperties;
    }

    public void putMap(String redisKey, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        RType type = redissonClient.getKeys().getType(redisKey);
        if (type != null
                && !"hash".equalsIgnoreCase(type.name())
                && !"map".equalsIgnoreCase(type.name())
                && !"none".equalsIgnoreCase(type.name())) {
            redissonClient.getKeys().delete(redisKey);
        }
        RMap<String, Object> map = redissonClient.getMap(redisKey);
        map.putAll(data);
        redissonClient.getKeys().expire(redisKey, cbServerProperties.getUserProfileKeysTtl(), TimeUnit.MILLISECONDS);
    }


    public Map<String, Object> getMap(String redisKey) {
        RMap<String, Object> map = redissonClient.getMap(redisKey);
        return map.readAllMap();
    }


    public void putStringList(String redisKey, List<String> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        RType type = redissonClient.getKeys().getType(redisKey);
        if (type != null && !"list".equalsIgnoreCase(type.name()) && !"none".equalsIgnoreCase(type.name())) {
            redissonClient.getKeys().delete(redisKey);
        }
        RList<String> redisList = redissonClient.getList(redisKey);
        redisList.clear();
        redisList.addAll(list);
        redissonClient.getKeys().expire(redisKey, cbServerProperties.getUserProfileKeysTtl(), TimeUnit.MILLISECONDS);
    }

    public List<String> getStringList(String redisKey) {
        RList<String> redisList = redissonClient.getList(redisKey);
        if (redisList == null || redisList.isEmpty()) {
            return Collections.emptyList();
        }
        return redisList.readAll();
    }
}
