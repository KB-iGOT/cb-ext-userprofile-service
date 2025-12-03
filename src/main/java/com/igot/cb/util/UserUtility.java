package com.igot.cb.util;

import java.util.List;
import java.util.Map;

import org.igot.common.crypto.DecryptionService;
import org.springframework.stereotype.Component;

@Component
public class UserUtility {
    private static DecryptionService decryptionService;

    public UserUtility(DecryptionService decryptionService) {
        UserUtility.decryptionService = decryptionService;
    }
    
    public static Map<String, Object> decryptSpecificUserData(Map<String, Object> userMap, List<String> fieldsToDecrypt) {
        for (String key : fieldsToDecrypt) {
            if (userMap.containsKey(key)) {
                userMap.put(key, decryptionService.decryptData((String) userMap.get(key), false));
            }
        }
        return userMap;
    }
}
