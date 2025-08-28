package com.igot.cb.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.entity.CustomFieldEntity;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceUpdateAdditionalFieldsTest {

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private CustomFieldRepository customFieldRepository;
    @Mock
    private EsUtilServiceImpl esUtilService;
    @Mock
    private ObjectMapper mapper;

    private String userId = "user123";
    private String orgId = "org123";
    private String authToken = "token123";

    @BeforeEach
    void setUp() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
    }

    @Test
    void testUpdateAdditionalFields_MissingCustomFieldId() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Each custom field must have a customFieldId. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            Map<String, Object> request = createValidRequest();
            List<Map<String, Object>> customFields = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);
            customFields.get(0).remove(Constants.CUSTOM_FIELD_ID);

            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Each custom field must have a customFieldId. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_MissingFieldType() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Each custom field must have a type. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            Map<String, Object> request = createValidRequest();
            List<Map<String, Object>> customFields = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);
            customFields.get(0).remove(Constants.FIELD_TYPE);

            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Each custom field must have a type. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_CustomFieldNotFound() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Custom field with ID field1 does not exist. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.empty());

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Custom field with ID field1 does not exist. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_CustomFieldNotActive() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Custom field with ID field1 is not active. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            CustomFieldEntity entity = createMockCustomFieldEntity(false);
            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Custom field with ID field1 is not active. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_WrongOrganization() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Custom field field1 is not configured for organization org123. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            CustomFieldEntity entity = createMockCustomFieldEntity(true);
            ObjectNode customFieldData = (ObjectNode) entity.getCustomFieldData();
            customFieldData.put(Constants.ORGANISATION_ID, "differentOrg");
            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Custom field field1 is not configured for organization org123. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_InvalidAttributeName() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Invalid attribute name for custom field field1. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            CustomFieldEntity entity = createMockCustomFieldEntity(true);
            ObjectNode customFieldData = (ObjectNode) entity.getCustomFieldData();
            customFieldData.put(Constants.ATTRIBUTE_NAME, "differentAttribute");
            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Invalid attribute name for custom field field1. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_TextFieldMissingValue() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Text field field1 must have a value. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            CustomFieldEntity entity = createMockCustomFieldEntity(true);
            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));

            Map<String, Object> request = createValidRequest();
            List<Map<String, Object>> customFields = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);
            customFields.get(0).remove(Constants.VALUE);

            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Text field field1 must have a value. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_TextFieldWrongType() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Custom field field1 is not of type text. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            CustomFieldEntity entity = createMockCustomFieldEntity(true);
            ObjectNode customFieldData = (ObjectNode) entity.getCustomFieldData();
            customFieldData.put(Constants.TYPE, Constants.MASTER_LIST);
            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Custom field field1 is not of type text. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_MasterListMissingValues() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("MasterList field field1 must have values. "), eq(HttpStatus.BAD_REQUEST))).thenAnswer(inv -> null);

            CustomFieldEntity entity = createMockCustomFieldEntity(true);
            ObjectNode customFieldData = (ObjectNode) entity.getCustomFieldData();
            customFieldData.put(Constants.TYPE, Constants.MASTER_LIST);
            when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));

            Map<String, Object> request = createMasterListRequest();
            List<Map<String, Object>> customFields = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);
            customFields.get(0).put(Constants.VALUES, Collections.emptyList());

            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("MasterList field field1 must have values. "), eq(HttpStatus.BAD_REQUEST)));
        }
    }

    @Test
    void testUpdateAdditionalFields_SaveContextDataFails() throws Exception {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Failed to save additional fields"), eq(HttpStatus.INTERNAL_SERVER_ERROR))).thenAnswer(inv -> null);

            setupValidMocks();
            when(mapper.writeValueAsString(any())).thenReturn("{}");
            when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(createFailureResponse());

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Failed to save additional fields"), eq(HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    }

    @Test
    void testUpdateAdditionalFields_ESUpdateFails() throws Exception {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Failed to update orgCustomFields in ES"), eq(HttpStatus.INTERNAL_SERVER_ERROR))).thenAnswer(inv -> null);

            setupValidMocks();
            when(mapper.writeValueAsString(any())).thenReturn("{}");
            when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(createSuccessResponse());
            when(esUtilService.updateUserOrgCustomFields(any(), any(), any())).thenReturn(false);

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Failed to update orgCustomFields in ES"), eq(HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    }

    @Test
    void testUpdateAdditionalFields_Success() throws Exception {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);

            setupValidMocks();
            when(mapper.writeValueAsString(any())).thenReturn("{}");
            when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(createSuccessResponse());
            when(esUtilService.updateUserOrgCustomFields(any(), any(), any())).thenReturn(true);

            Map<String, Object> request = createValidRequest();
            ApiResponse response = profileService.updateAdditionalFields(request, authToken);

            assertEquals(HttpStatus.OK, response.getResponseCode());
            assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        }
    }

    @Test
    void testUpdateAdditionalFields_Exception() {
        try (MockedStatic<ProjectUtil> mockedProjectUtil = mockStatic(ProjectUtil.class)) {
            ApiResponse mockResponse = new ApiResponse();
            mockedProjectUtil.when(() -> ProjectUtil.createDefaultResponse("api.update.additionalFields")).thenReturn(mockResponse);
            mockedProjectUtil.when(() -> ProjectUtil.errorResponse(any(), eq("Internal server error"), eq(HttpStatus.INTERNAL_SERVER_ERROR))).thenAnswer(inv -> null);

            setupValidMocks();
            when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("DB error"));

            Map<String, Object> request = createValidRequest();
            profileService.updateAdditionalFields(request, authToken);

            mockedProjectUtil.verify(() -> ProjectUtil.errorResponse(any(), eq("Internal server error"), eq(HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    }

    private Map<String, Object> createValidRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID, userId);
        request.put(Constants.ORGANISATION_ID, orgId);
        
        Map<String, Object> customField = new HashMap<>();
        customField.put(Constants.CUSTOM_FIELD_ID, "field1");
        customField.put(Constants.FIELD_TYPE, Constants.TEXT);
        customField.put(Constants.ATTRIBUTE_NAME, "testAttribute");
        customField.put(Constants.VALUE, "testValue");
        
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of(customField));
        return request;
    }

    private Map<String, Object> createMasterListRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.USER_ID, userId);
        request.put(Constants.ORGANISATION_ID, orgId);
        
        Map<String, Object> customField = new HashMap<>();
        customField.put(Constants.CUSTOM_FIELD_ID, "field1");
        customField.put(Constants.FIELD_TYPE, Constants.MASTER_LIST);
        customField.put(Constants.ATTRIBUTE_NAME, "testAttribute");
        
        Map<String, Object> value = new HashMap<>();
        value.put(Constants.ATTRIBUTE_NAME, "testAttribute");
        value.put(Constants.VALUE, "testValue");
        value.put(Constants.LEVEL, 1);
        
        customField.put(Constants.VALUES, List.of(value));
        request.put(Constants.CUSTOM_FIELD_VALUES, List.of(customField));
        return request;
    }

    private CustomFieldEntity createMockCustomFieldEntity(boolean isActive) {
        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setIsActive(isActive);
        
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode customFieldData = objectMapper.createObjectNode();
        customFieldData.put(Constants.ORGANISATION_ID, orgId);
        customFieldData.put(Constants.ATTRIBUTE_NAME, "testAttribute");
        customFieldData.put(Constants.TYPE, Constants.TEXT);
        
        ArrayNode fieldData = objectMapper.createArrayNode();
        ObjectNode fieldValue = objectMapper.createObjectNode();
        fieldValue.put(Constants.FIELD_VALUE, "testValue");
        fieldData.add(fieldValue);
        customFieldData.set(Constants.CUSTOM_FIELD_DATA, fieldData);
        
        entity.setCustomFieldData(customFieldData);
        return entity;
    }

    private void setupValidMocks() {
        CustomFieldEntity entity = createMockCustomFieldEntity(true);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("field1")).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
    }

    private ApiResponse createSuccessResponse() {
        ApiResponse response = new ApiResponse();
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    private ApiResponse createFailureResponse() {
        ApiResponse response = new ApiResponse();
        response.put(Constants.RESPONSE, "FAILURE");
        return response;
    }
}