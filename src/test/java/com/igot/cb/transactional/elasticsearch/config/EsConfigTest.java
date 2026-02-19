package com.igot.cb.transactional.elasticsearch.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class EsConfigTest {

    @Test
    void testUserEsClient_fullCoverage() throws Exception {
        EsConfig esConfig = new EsConfig();

        // Inject values via Reflection (host includes port in format "host:port")
        setField(esConfig, "userEsClientHost", "localhost:9200,127.0.0.1:9201");
        setField(esConfig, "userEsClientUsername", "testuser");
        setField(esConfig, "userEsClientPassword", "testpass");

        // Invoke the method
        RestHighLevelClient client = esConfig.userEsClient();

        assertNotNull(client);

        // Close the client to cover closing resources (optional)
        client.close();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = EsConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
