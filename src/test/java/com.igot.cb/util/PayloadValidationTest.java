package com.igot.cb.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.exceptions.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class PayloadValidationTest {

    private PayloadValidation payloadValidator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        payloadValidator = new PayloadValidation();
        mapper = new ObjectMapper();
        setupTestResources();
    }

    private void setupTestResources() {
        try {
            Path schemaDir = Paths.get("src/test/resources/schema");
            Files.createDirectories(schemaDir);
            String validSchema = "{\n" +
                    "  \"$schema\": \"http://json-schema.org/draft-04/schema#\",\n" +
                    "  \"title\": \"User\",\n" +
                    "  \"type\": \"object\",\n" +
                    "  \"properties\": {\n" +
                    "    \"id\": {\n" +
                    "      \"type\": \"integer\"\n" +
                    "    },\n" +
                    "    \"name\": {\n" +
                    "      \"type\": \"string\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"required\": [\"id\", \"name\"],\n" +
                    "  \"additionalProperties\": false\n" +
                    "}";
            Files.writeString(schemaDir.resolve("valid-schema.json"), validSchema);
            String invalidSchema = "{\n" +
                    "  \"$schema\": \"http://json-schema.org/draft-04/schema#\"\n" + // missing comma
                    "  \"type\": \"object\",\n" +
                    "  \"properties\": {\n" +
                    "    \"id\": {\n" +
                    "      \"type\": \"integer\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";
            Files.writeString(schemaDir.resolve("invalid-schema.json"), invalidSchema);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testValidatePayload_singleObject_success() throws Exception {
        String schemaFile = "/schema/valid-schema.json";
        String jsonData = "{\"id\": 1, \"name\": \"John Doe\"}";
        JsonNode payload = mapper.readTree(jsonData);

        assertDoesNotThrow(() -> payloadValidator.validatePayload(schemaFile, payload));
    }

    @Test
    void testValidatePayload_arrayOfObjects_success() throws Exception {
        String schemaFile = "/schema/valid-schema.json";
        String jsonData = "[{\"id\": 1, \"name\": \"John\"}, {\"id\": 2, \"name\": \"Jane\"}]";
        JsonNode payload = mapper.readTree(jsonData);
        assertDoesNotThrow(() -> payloadValidator.validatePayload(schemaFile, payload));
    }

    @Test
    void testValidatePayload_invalidSingleObject_throwsException() throws Exception {
        String schemaFile = "/schema/valid-schema.json";
        String jsonData = "{\"id\": \"not-a-number\", \"name\": 123}";
        JsonNode payload = mapper.readTree(jsonData);
        CustomException exception = assertThrows(CustomException.class,
                () -> payloadValidator.validatePayload(schemaFile, payload)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatusCode());
        assertTrue(exception.getMessage().contains("Validation error"));
    }

    @Test
    void testValidatePayload_invalidArrayElement_throwsException() throws Exception {
        String schemaFile = "/schema/valid-schema.json";
        String jsonData = "[{\"id\": 1, \"name\": \"John\"}, {\"id\": \"invalid\", \"name\": 123}]";
        JsonNode payload = mapper.readTree(jsonData);
        CustomException exception = assertThrows(CustomException.class,
                () -> payloadValidator.validatePayload(schemaFile, payload)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatusCode());
    }

    @Test
    void testValidatePayload_missingSchema_throwsException() throws Exception {
        String schemaFile = "/schema/non-existent-schema.json";
        String jsonData = "{\"id\": 1, \"name\": \"John\"}";
        JsonNode payload = mapper.readTree(jsonData);
        CustomException exception = assertThrows(CustomException.class,
                () -> payloadValidator.validatePayload(schemaFile, payload)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatusCode());
    }

    @Test
    void testValidatePayload_invalidSchema_throwsException() throws Exception {
        String schemaFile = "/schema/invalid-schema.json";
        String jsonData = "{\"id\": 1, \"name\": \"John\"}";
        JsonNode payload = mapper.readTree(jsonData);

        assertThrows(CustomException.class,
                () -> payloadValidator.validatePayload(schemaFile, payload)
        );
    }
}