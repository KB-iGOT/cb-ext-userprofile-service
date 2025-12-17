package com.igot.cb.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.igot.common.crypto.DecryptionService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UserUtilityTest {

    @Mock
    private DecryptionService mockDecryptionService;

    @InjectMocks
    private UserUtility userUtility;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Initialize UserUtility with the mocked DecryptionService
        new UserUtility(mockDecryptionService);
    }

    @Test
    public void testDecryptSpecificUserData() {
        when(mockDecryptionService.decryptData(anyString(), eq(false)))
            .thenAnswer(i -> "decrypted_" + i.getArgument(0));

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", "encrypted_email@test.com");
        userMap.put("phone", "encrypted_1234567890");
        userMap.put("name", "John Doe");

        List<String> fieldsToDecrypt = Arrays.asList("email", "phone", "address");

        Map<String, Object> result = userUtility.decryptSpecificUserData(userMap, fieldsToDecrypt);

        assertEquals("decrypted_encrypted_email@test.com", result.get("email"));
        assertEquals("decrypted_encrypted_1234567890", result.get("phone"));
        assertEquals("John Doe", result.get("name"));
        assertFalse(result.containsKey("address"));

        verify(mockDecryptionService).decryptData("encrypted_email@test.com", false);
        verify(mockDecryptionService).decryptData("encrypted_1234567890", false);
        verify(mockDecryptionService, times(2)).decryptData(anyString(), eq(false));
    }

    @Test
    public void testDecryptSpecificUserDataWithEmptyMap() {
        Map<String, Object> userMap = new HashMap<>();
        List<String> fieldsToDecrypt = Arrays.asList("email", "phone");

        Map<String, Object> result = userUtility.decryptSpecificUserData(userMap, fieldsToDecrypt);

        assertTrue(result.isEmpty());
        verify(mockDecryptionService, never()).decryptData(anyString(), anyBoolean());
    }

    @Test
    public void testDecryptSpecificUserDataWithNullValues() {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", null);

        List<String> fieldsToDecrypt = Arrays.asList("email");

        Map<String, Object> result = userUtility.decryptSpecificUserData(userMap, fieldsToDecrypt);

        assertNull(result.get("email"));
        verify(mockDecryptionService, never()).decryptData(anyString(), anyBoolean());
    }
}
