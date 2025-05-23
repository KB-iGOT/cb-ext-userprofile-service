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


  @Value("${redis.insights.index}")
  private int redisInsightIndex;

  @Value("${search.result.redis.ttl}")
  private long searchResultRedisTtl;

  @Value("${sb.api.key}")
  private String sbApiKey;

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

  @Value("${validation.required-fields.educationalQualification}")
  private String educationalQualificationMandatoryFields;

  @Value("${validation.required-fields.serviceHistory}")
  private String serviceHistoryMandatoryFields;

  @Value("${validation.required-fields.achievement}")
  private String achievementsMandatoryFields;

  @Value("${context.types}")
  private String[] contextType;

  @Value("${basic.profile.fields}")
  private String basicProfileFields;

  @Value("${profile.completion.required.fields}")
  private String profileCompletionRequiredFields;

  @Value("${profile.completion.extended.fields}")
  private List<String> extendedFieldsConfig;

  @Value("${profile.completion.field.weight}")
  private double fieldWeight;

  public List<String> getBasicProfileFields() {
    return Arrays.asList(basicProfileFields.split(","));
  }

  public List<String> getProfileCompletionRequiredFields() {
    return Arrays.asList(profileCompletionRequiredFields.split(","));
  }

}
