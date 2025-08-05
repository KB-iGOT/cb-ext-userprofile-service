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
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.Mockito;
import org.mockito.Spy;

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
    public void testInit_shouldHandleExceptionInFilesWalk() throws Exception {
        // Simulate Files.walk throwing exception
        when(propertiesCache.getProperty(eq(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH)))
                .thenReturn("/non/existent/path");
        // Should not throw, just log
        keyManager.init();
        // No keys loaded
        assertNull(keyManager.getPublicKey("any.pem"));
    }

    @Test
    public void testInit_shouldHandleExceptionInReadingFile() throws Exception {
        // Create a file with invalid content
        Path pubKeyFile = tempDir.resolve("bad_key.pem");
        Files.write(pubKeyFile, "not-a-key".getBytes(StandardCharsets.UTF_8));
        when(propertiesCache.getProperty(eq(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH)))
                .thenReturn(tempDir.toString());
        // Should not throw, just log
        keyManager.init();
        // Key should not be loaded
        assertNull(keyManager.getPublicKey("bad_key.pem"));
    }

    @Test
    public void testConstructorAndGetPublicKey() {
        KeyManager km = new KeyManager(propertiesCache);
        // Should return null for any key
        assertNull(km.getPublicKey("foo.pem"));
    }

    @Test
    public void testLoadPublicKey_validKey() throws Exception {
        PublicKey pubKey = generateTestKey();
        String publicKeyContent = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(pubKey.getEncoded()) + "\n" +
                "-----END PUBLIC KEY-----";
        PublicKey loaded = KeyManager.loadPublicKey(publicKeyContent);
        assertNotNull(loaded);
        assertEquals(pubKey.getAlgorithm(), loaded.getAlgorithm());
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

