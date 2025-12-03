package com.igot.cb.util;

import java.util.List;
import java.util.Map;

import org.igot.common.crypto.DecryptionService;
import org.springframework.stereotype.Component;

@Component
public class UserUtility {
    private final DecryptionService decryptionService;

    public UserUtility(DecryptionService decryptionService) {
        this.decryptionService = decryptionService;
    }
    
    public Map<String, Object> decryptSpecificUserData(Map<String, Object> userMap, List<String> fieldsToDecrypt) {
        for (String key : fieldsToDecrypt) {
            userMap.computeIfPresent(key, (k, value) ->
                decryptionService.decryptData((String) value, false)
            );
        }
        return userMap;
    }
}
