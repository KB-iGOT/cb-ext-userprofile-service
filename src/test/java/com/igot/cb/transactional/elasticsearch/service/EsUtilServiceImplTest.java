package com.igot.cb.transactional.elasticsearch.service;

import com.igot.cb.util.CbServerProperties;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class EsUtilServiceImplTest {

    @Mock
    private RestHighLevelClient mockClient;

    @Mock
    private CbServerProperties mockProperties;

    @InjectMocks
    private EsUtilServiceImpl esUtilService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockProperties.getUserProfileIndex()).thenReturn("user-profile-index");
        
        // Inject cbProperties using reflection since @InjectMocks doesn't work with @Autowired fields
        Field cbPropertiesField = EsUtilServiceImpl.class.getDeclaredField("cbProperties");
        cbPropertiesField.setAccessible(true);
        cbPropertiesField.set(esUtilService, mockProperties);
    }

    @Test
    void testUpdateUserOrgCustomFields_Success() throws Exception {
        // Arrange
        String userId = "user123";
        String orgId = "org123";
        List<Map<String, Object>> orgCustomFields = new ArrayList<>();

        // Mock successful call
        UpdateResponse mockResponse = mock(UpdateResponse.class);
        when(mockClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(mockResponse);

        // Act
        Boolean result = esUtilService.updateUserOrgCustomFields(userId, orgId, orgCustomFields);

        // Assert
        assertTrue(result);
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void testUpdateUserOrgCustomFields_Failure() throws Exception {
        // Arrange
        String userId = "user123";
        String orgId = "org123";
        List<Map<String, Object>> orgCustomFields = new ArrayList<>();

        // Mock exception
        doThrow(new RuntimeException("Update failed"))
                .when(mockClient).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));

        // Act
        Boolean result = esUtilService.updateUserOrgCustomFields(userId, orgId, orgCustomFields);

        // Assert
        assertFalse(result);
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }
}
