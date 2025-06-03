package com.igot.cb.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class UserUtility {


    private static List<String> userKeyToDecrypt;
    private static DecryptionService decryptionService;

    static {
        init();
    }

    public static Map<String, Object> decryptSpecificUserData(
            Map<String, Object> userMap, List<String> fieldsToDecrypt) {
        DecryptionService service = ServiceFactory.getDecryptionServiceInstance();
        for (String key : fieldsToDecrypt) {
            if (userMap.containsKey(key)) {
                userMap.put(key, service.decryptData((String) userMap.get(key), false));
            }
        }
        return userMap;
    }

    private static void init() {
        decryptionService = ServiceFactory.getDecryptionServiceInstance();
        String userKey = PropertiesCache.getInstance().getProperty("userkey.encryption");
        String userKeyDecrypt = PropertiesCache.getInstance().getProperty("userkey.decryption");
        String userKeyToMasked = PropertiesCache.getInstance().getProperty("userkey.masked");
        userKeyToDecrypt = new ArrayList<>(Arrays.asList(userKeyDecrypt.split(",")));
    }

}
