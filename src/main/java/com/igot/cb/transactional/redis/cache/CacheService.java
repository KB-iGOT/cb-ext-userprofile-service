package com.igot.cb.transactional.redis.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CacheService {

  @Autowired
  RedisTemplate<String, String> redisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Value("${spring.redis.cacheTtl}")
  private long cacheTtl;

  public void putCache(String key, Object object) {
    try {
      String data = objectMapper.writeValueAsString(object);
      redisTemplate.opsForValue().set(key, data, cacheTtl, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("Error while putting data in Redis cache: {} ", e.getMessage());
    }
  }

  public String getCache(String key) {
    try {
      return redisTemplate.opsForValue().get(key);
    } catch (Exception e) {
      log.error("Error while getting data from Redis cache: {} ", e.getMessage());
      return null;
    }
  }

  public void deleteCache(String key) {
    boolean result = redisTemplate.delete(key);
    if (result) {
      log.info("Field deleted successfully from key {}.", key);
    } else {
      log.warn("Field not found in key {}.", key);
    }
  }

  public void putCache(String key, String jsonString) {
    try {
      redisTemplate.opsForValue().set(key, jsonString, cacheTtl, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("Error while putting data in Redis cache: {} ", e.getMessage());
    }
  }

  public Map<String, String> getCourseMetadataAsJsonString(List<String> courseIds) {
    if (courseIds == null || courseIds.isEmpty())
      return Map.of();

    // Redis stores raw JSON under keys like "do_<id>", no prefix needed
    List<String> keys = new ArrayList<>(courseIds);

    List<String> values = redisTemplate.opsForValue().multiGet(keys);

    // Safely pair each courseId to its JSON string (handling nulls)
    Map<String, String> result = new LinkedHashMap<>();

    if (CollectionUtils.isEmpty(values)) {
      return result;
    }

    if (keys.size() != values.size()) {
      log.error("Failed to get the course details from Redis Cache. KeySize: {}, Value retrieved: {}", keys.size(),
          values.size());
          return result;
    }

    for (int i = 0; i < keys.size(); i++) {
      String json = values.get(i);
      if (json != null) {
        result.put(keys.get(i), json);
      }
    }

    return result;
  }

}
