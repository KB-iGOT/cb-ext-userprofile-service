package com.igot.cb;

import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CbExtUserProfileApplicationTest {

    @Mock
    private CbServerProperties serverProperties;

    @Test
    public void testRestTemplateCreation() {
        // Configure mock
        when(serverProperties.getRequestTimeoutMs()).thenReturn(5000);
        when(serverProperties.getMaxTotalConnections()).thenReturn(100);
        when(serverProperties.getMaxConnectionsPerRoute()).thenReturn(20);

        // Create application instance with mocked properties
        CbExtUserProfileApplication application = new CbExtUserProfileApplication(serverProperties);

        // Test RestTemplate creation
        RestTemplate restTemplate = application.restTemplate();
        assertNotNull(restTemplate);

        // Verify RestTemplate configuration
        assertTrue(restTemplate.getRequestFactory() instanceof HttpComponentsClientHttpRequestFactory);
    }

    @Test
    public void testMainMethod() {
        // Use MockedStatic to mock the static SpringApplication.run method
        try (MockedStatic<SpringApplication> mockedStatic = Mockito.mockStatic(SpringApplication.class)) {
            // Mock the static method to do nothing
            mockedStatic.when(() -> SpringApplication.run(
                    eq(CbExtUserProfileApplication.class),
                    any(String[].class))
            ).thenReturn(null);

            // Execute the main method
            String[] args = {};
            CbExtUserProfileApplication.main(args);

            // Verify the method was called
            mockedStatic.verify(() -> SpringApplication.run(
                    eq(CbExtUserProfileApplication.class),
                    any(String[].class)));
        }
    }
}