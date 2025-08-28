package com.igot.cb.transactional.elasticsearch.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class EsConfigTest {

    @Test
    void testSbESClient_fullCoverage() throws Exception {
        EsConfig esConfig = new EsConfig();

        // Inject values via Reflection
        setField(esConfig, "sbESClientHost", "localhost,127.0.0.1");
        setField(esConfig, "sbESClientPort", "9200,9201");
        setField(esConfig, "sbESClientUsername", "testuser");
        setField(esConfig, "sbESClientPassword", "testpass");

        // Invoke the method
        RestHighLevelClient client = esConfig.sbESClient();

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
