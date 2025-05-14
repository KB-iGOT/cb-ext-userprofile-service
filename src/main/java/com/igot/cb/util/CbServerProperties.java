package com.igot.cb.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Getter
@Setter
public class CbServerProperties {

  @Value("${default.content.properties}")
  private String defaultContentProperties;

  @Value("${content-service-host}")
  private String contentHost;

  @Value("${content-read-endpoint}")
  private String contentReadEndPoint;

  @Value("${content-read-endpoint-fields}")
  private String contentReadEndPointFields;

  @Value("${redis.insights.index}")
  private int redisInsightIndex;

  @Value("${search.result.redis.ttl}")
  private long searchResultRedisTtl;

  @Value("${sb.api.key}")
  private String sbApiKey;

  @Value("${learner.service.url}")
  private String learnerServiceUrl;

  @Value("${sb.org.search.path}")
  private String orgSearchPath;

  @Value("${notify.service.host}")
  private String notifyServiceHost;

  @Value("${notify.service.path.async}")
  private String notificationAsyncPath;


  @Value("${notification.support.mail}")
  private String supportEmail;

  @Value("${learner.service.url}")
  private String sbUrl;

  @Value("${sunbird.user.search.endpoint}")
  private String userSearchEndPoint;

  @Value("${lms.user.read.path}")
  private String userReadEndPoint;

  @Value("${sb.search.service.host}")
  private String sbSearchServiceHost;

  @Value("${sb.composite.v4.search}")
  private String sbCompositeV4Search;

  @Value("${http.client.request.factory.timeout}")
  private int requestTimeoutMs;

  @Value("${http.pooling.client.cm.max.total.connections}")
  private int maxTotalConnections;

  @Value("${http.pooling.client.cm.default.max.per.route}")
  private int maxConnectionsPerRoute;

  @Value("${redis.pool.max.total}")
  private int redisPoolMaxTotal;

  @Value("${redis.pool.max.idle}")
  private int redisPoolMaxIdle;

  @Value("${redis.pool.min.idle}")
  private int redisPoolMinIdle;

  @Value("${redis.pool.max.wait}")
  private int redisPoolMaxWait;

  @Value("${redis.connection.timeout}")
  private long redisConnectionTimeout;
}
