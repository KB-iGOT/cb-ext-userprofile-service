package com.igot.cb.authentication.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Comparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.igot.cb.authentication.model.KeyData;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

@RunWith(MockitoJUnitRunner.class)
public class KeyManagerTest {

    @InjectMocks
    private KeyManager keyManager;

    @Mock
    private PropertiesCache propertiesCache;

    private static final String TEMP_PUBLIC_KEY_FILE = "temp_public_key.pem";
    private Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("keymanager-test");
    }

    @After
    public void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void testInit_shouldLoadPublicKeysSuccessfully() throws Exception {
        // Create dummy public key file
        String publicKeyContent = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(generateTestKey().getEncoded()) + "\n" +
                "-----END PUBLIC KEY-----";
        Path pubKeyFile = tempDir.resolve(TEMP_PUBLIC_KEY_FILE);
        Files.write(pubKeyFile, publicKeyContent.getBytes(StandardCharsets.UTF_8));

        // Mock base path
        when(propertiesCache.getProperty(eq(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH)))
                .thenReturn(tempDir.toString());

        // Call init
        keyManager.init();

        // Verify key is loaded
        KeyData keyData = keyManager.getPublicKey(TEMP_PUBLIC_KEY_FILE);
        assertNotNull(keyData);
        assertEquals(TEMP_PUBLIC_KEY_FILE, keyData.getKeyId());
        assertNotNull(keyData.getPublicKey());
    }

    @Test
    public void testLoadPublicKey_shouldThrowExceptionOnInvalidKey() {
        String invalidKey = "-----BEGIN PUBLIC KEY-----\nInvalidKey\n-----END PUBLIC KEY-----";

        try {
            KeyManager.loadPublicKey(invalidKey);
            fail("Expected an exception due to invalid key");
        } catch (Exception e) {
            // success
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testGetPublicKey_shouldReturnNullWhenNotPresent() {
        assertNull(keyManager.getPublicKey("non-existent-key"));
    }

    private PublicKey generateTestKey() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair().getPublic();
    }
}