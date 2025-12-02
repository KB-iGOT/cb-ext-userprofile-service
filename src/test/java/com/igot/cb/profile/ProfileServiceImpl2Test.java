package com.igot.cb.profile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.util.Constants;

import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImpl2Test {

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private Logger log;

    private final String userId = "user123";
    private final String rootOrgId = "org001";

    @BeforeEach
    void setUp() {
        // Setup done by MockitoExtension
    }

    @Test
    void testGetUserRoles_withScopeAsList_matchingOrg() {
        List<Map<String, Object>> userRoleList = new ArrayList<>();
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("role", "admin");
        roleMap.put("scope", List.of(Map.of("organisationId", rootOrgId)));
        userRoleList.add(roleMap);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(userRoleList);

        List<String> result = profileService.getUserRoles(userId, rootOrgId);
        assertEquals(List.of("admin"), result);
    }

    @Test
    void testGetUserRoles_withScopeAsList_nonMatchingOrg() {
        List<Map<String, Object>> userRoleList = new ArrayList<>();
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("role", "admin");
        roleMap.put("scope", List.of(Map.of("organisationId", "otherOrg")));
        userRoleList.add(roleMap);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(userRoleList);

        List<String> result = profileService.getUserRoles(userId, rootOrgId);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testGetUserRoles_withScopeAsString_validJson_matchingOrg() throws Exception {
        List<Map<String, Object>> userRoleList = new ArrayList<>();
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("role", "viewer");
        roleMap.put("scope", "[{\"organisationId\":\"org001\"}]");
        userRoleList.add(roleMap);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(userRoleList);
        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(List.of(Map.of("organisationId", rootOrgId)));

        List<String> result = profileService.getUserRoles(userId, rootOrgId);
        assertEquals(List.of("viewer"), result);
    }

    @Test
    void testGetUserRoles_withScopeAsString_invalidJson() throws Exception {
        List<Map<String, Object>> userRoleList = new ArrayList<>();
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("role", "viewer");
        roleMap.put("scope", "[{\"organisationId\":\"org001\"");
        userRoleList.add(roleMap);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(userRoleList);
        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("JSON error"));

        List<String> result = profileService.getUserRoles(userId, rootOrgId);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testGetUserRoles_withEmptyScope() {
        List<Map<String, Object>> userRoleList = new ArrayList<>();
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("role", "editor");
        roleMap.put("scope", Collections.emptyList());
        userRoleList.add(roleMap);

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(userRoleList);

        List<String> result = profileService.getUserRoles(userId, rootOrgId);
        assertEquals(Collections.emptyList(), result);
    }


    @Test
    void testGetUserRoles_withValidScopesAsList() {
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.ROLE, "admin");
        localRecord.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));
        when(cassandraOperation.getRecordsByProperties(
                any(), any(), any(), any(), any()
        )).thenReturn(List.of(localRecord));

        List<String> roles = profileService.getUserRoles(userId, rootOrgId);

        assertEquals(List.of("admin"), roles);
    }

    @Test
    void testGetUserRoles_withScopeAsJsonString_valid() throws Exception {
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.ROLE, "manager");
        localRecord.put(Constants.SCOPE, "[{\"organisationId\":\"org001\"}]");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(localRecord));

        when(mapper.readValue(anyString(), ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()))
                .thenReturn(List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));

        List<String> roles = profileService.getUserRoles(userId, rootOrgId);

        assertEquals(List.of("manager"), roles);
    }

    @Test
    void testGetUserRoles_withScopeAsJsonString_invalid() throws Exception {
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.ROLE, "user");
        localRecord.put(Constants.SCOPE, "[invalid_json]");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(localRecord));

        when(mapper.readValue(anyString(), ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()))
                .thenThrow(new RuntimeException("JSON parse error"));

        List<String> roles = profileService.getUserRoles(userId, rootOrgId);

        assertTrue(roles.isEmpty());
    }

    @Test
    void testGetUserRoles_withEmptyScopeList() {
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.ROLE, "guest");
        localRecord.put(Constants.SCOPE, List.of()); // Empty scopes

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(localRecord));

        List<String> roles = profileService.getUserRoles(userId, rootOrgId);

        assertTrue(roles.isEmpty());
    }

    @Test
    void testGetUserRoles_withMixedRolesAndDuplicates() {
        Map<String, Object> record1 = new HashMap<>();
        record1.put(Constants.ROLE, "admin");
        record1.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));

        Map<String, Object> record2 = new HashMap<>();
        record2.put(Constants.ROLE, "admin"); // duplicate
        record2.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), any()))
                .thenReturn(List.of(record1, record2));

        List<String> roles = profileService.getUserRoles(userId, rootOrgId);

        assertEquals(List.of("admin"), roles);
    }

}
