package com.igot.cb.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.igot.common.ApiResponse;
import org.igot.common.PropertiesCache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * This class will contains all the common utility methods.
 *
 * @author Karthikeyan R
 */
@Component
@Slf4j
public class ProjectUtil {

    private PropertiesCache propertiesCache;
    private ObjectMapper mapper;

    public ProjectUtil(PropertiesCache propertiesCache, ObjectMapper mapper) {
        this.propertiesCache = propertiesCache;
        this.mapper = mapper;
    }


    TypeReference<List<Map<String, Object>>> listOfMapType = new TypeReference<List<Map<String, Object>>>() {
    };

    TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {
    };

    public static void errorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.setResponseCode(httpStatus);
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(Constants.FAILED);
    }

    public List<Map<String, Object>> parseListOfMap(String json) throws IOException {
        return mapper.readValue(json, listOfMapType);
    }

    public Map<String, Object> parseMap(String json) throws IOException {
        return mapper.readValue(json, mapType);
    }

    public String convertToString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            log.error("Error converting object to string: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getConfigValue(String key) {
        return propertiesCache.getProperty(key);
    }

    public String buildCacheKey(String prefix, String contextType, String userId) {
        return String.join(":", prefix, contextType, userId);
    }
}
