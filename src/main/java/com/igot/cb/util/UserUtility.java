package com.igot.cb.util;

import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

public class UserUtility {

    private UserUtility() {
    }

    static {
        init();
    }

    public static Map<String, Object> decryptSpecificUserData(
            Map<String, Object> userMap, List<String> fieldsToDecrypt) {
        if (MapUtils.isEmpty(userMap) || CollectionUtils.isEmpty(fieldsToDecrypt)) {
            // Nothing to decrypt, and nothing to short-circuit: skip the loop entirely so an
            // empty/immutable map (e.g. Map.of()) is returned untouched instead of failing
            // computeIfPresent() below, which throws UnsupportedOperationException on an
            // immutable map even when the key being looked up is absent.
            return userMap;
        }
        DecryptionService service = ServiceFactory.getDecryptionServiceInstance();
        for (String key : fieldsToDecrypt) {
            userMap.computeIfPresent(key, (k, v) -> service.decryptData((String) v, false));
        }
        return userMap;
    }

    private static void init() {
        PropertiesCache.getInstance().getProperty("userkey.encryption");
        PropertiesCache.getInstance().getProperty("userkey.decryption");
        PropertiesCache.getInstance().getProperty("userkey.masked");
    }

}
