package com.igot.cb.campaign.service;

import com.igot.cb.common.KafkaEventPublisher;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    private static final String TEST_TOPIC = "special.campaign.nlh.sevabhav26";

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private CbServerProperties cbServerProperties;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private Map<String, Object> validPayload;

    @BeforeEach
    void setUp() {
        validPayload = new HashMap<>();
        validPayload.put("campaignId", "Seva Bhav");
        validPayload.put("email", "john.doe@example.com");
        validPayload.put("phone", "9876543210");
        validPayload.put("name", "John Doe");
        validPayload.put("consent", true);
        validPayload.put("consentVersion", 1);
        validPayload.put("source", "Web Portal");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateLead_Success() {
        when(cbServerProperties.getCampaignTopicName()).thenReturn(TEST_TOPIC);

        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(Constants.SUCCESS, response.getResult().get(Constants.RESPONSE));

        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaEventPublisher, times(1)).publish(eq(TEST_TOPIC), eq("9876543210"), eventCaptor.capture());
        Map<String, Object> event = eventCaptor.getValue();
        assertEquals("john.doe@example.com", event.get(Constants.EMAIL));
        assertNotNull(event.get(Constants.ETS));
        assertTrue(((Long) event.get(Constants.ETS)) > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateLead_Success_WithoutEmail() {
        when(cbServerProperties.getCampaignTopicName()).thenReturn(TEST_TOPIC);
        validPayload.remove("email");

        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());

        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaEventPublisher, times(1)).publish(eq(TEST_TOPIC), eq("9876543210"), eventCaptor.capture());
        Map<String, Object> event = eventCaptor.getValue();
        assertNull(event.get(Constants.EMAIL));
        assertNotNull(event.get(Constants.ETS));
        assertTrue(((Long) event.get(Constants.ETS)) > 0);
    }

    @Test
    void testCreateLead_Success_WithBlankEmail() {
        when(cbServerProperties.getCampaignTopicName()).thenReturn(TEST_TOPIC);
        validPayload.put("email", "   ");

        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        verify(kafkaEventPublisher, times(1)).publish(eq(TEST_TOPIC), eq("9876543210"), anyMap());
    }

    @Test
    void testCreateLead_Success_WrappedInRequestAndDataObject() {
        when(cbServerProperties.getCampaignTopicName()).thenReturn(TEST_TOPIC);
        Map<String, Object> dataWrapper = new HashMap<>();
        dataWrapper.put(Constants.DATA, validPayload);
        Map<String, Object> requestWrapper = new HashMap<>();
        requestWrapper.put(Constants.REQUEST, dataWrapper);

        ApiResponse response = campaignService.createLead(requestWrapper);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        verify(kafkaEventPublisher, times(1)).publish(eq(TEST_TOPIC), eq("9876543210"), anyMap());
    }

    @Test
    void testCreateLead_Success_WrappedInRequestObject() {
        when(cbServerProperties.getCampaignTopicName()).thenReturn(TEST_TOPIC);
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put(Constants.REQUEST, validPayload);

        ApiResponse response = campaignService.createLead(wrapper);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        verify(kafkaEventPublisher, times(1)).publish(eq(TEST_TOPIC), eq("9876543210"), anyMap());
    }

    @Test
    void testCreateLead_MobileAttributeNotAccepted_ReturnsBadRequest() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", "Seva Bhav");
        payload.put("mobile", "9123456789");
        payload.put("name", "Jane Doe");
        payload.put("consent", true);
        payload.put("consentVersion", 2);
        payload.put("source", "Web Portal");

        ApiResponse response = campaignService.createLead(payload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("phone cannot be null or empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_EmptyBody_ReturnsBadRequest() {
        ApiResponse response = campaignService.createLead(new HashMap<>());

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Request body cannot be empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_MissingCampaignId_ReturnsBadRequest() {
        validPayload.remove("campaignId");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("campaignId cannot be null or empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidCampaignId_ReturnsBadRequest() {
        validPayload.put("campaignId", "Other Campaign");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid campaignid. Only 'Seva Bhav' is accepted", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidEmailPattern_ReturnsBadRequest() {
        validPayload.put("email", "invalid-email-format");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid email pattern", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_MissingPhone_ReturnsBadRequest() {
        validPayload.remove("phone");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("phone cannot be null or empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidPhone_FewerThan10Digits() {
        validPayload.put("phone", "12345");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid mobile number. It must be a 10-digit number", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidPhone_MoreThan10Digits() {
        validPayload.put("phone", "123456789012");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid mobile number. It must be a 10-digit number", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidPhone_NonNumeric() {
        validPayload.put("phone", "98765abcde");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid mobile number. It must be a 10-digit number", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_MissingName_ReturnsBadRequest() {
        validPayload.remove("name");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("name cannot be null or empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_MissingConsent_ReturnsBadRequest() {
        validPayload.remove("consent");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("consent cannot be null", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidConsent_ReturnsBadRequest() {
        validPayload.put("consent", "not-a-boolean");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("consent must be a boolean value", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_MissingConsentVersion_ReturnsBadRequest() {
        validPayload.remove("consentVersion");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("consentVersion cannot be null or empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_InvalidConsentVersion_NotPositive() {
        validPayload.put("consentVersion", 0);
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("consentVersion must be a valid positive number", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_MissingSource_ReturnsBadRequest() {
        validPayload.remove("source");
        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("source cannot be null or empty", response.getParams().getErrMsg());
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void testCreateLead_PublishThrowsException_ReturnsInternalServerError() {
        when(cbServerProperties.getCampaignTopicName()).thenReturn(TEST_TOPIC);
        doThrow(new RuntimeException("Kafka connection timed out"))
                .when(kafkaEventPublisher).publish(anyString(), anyString(), anyMap());

        ApiResponse response = campaignService.createLead(validPayload);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Internal server error occurred while processing campaign lead", response.getParams().getErrMsg());
    }

    @Test
    void testValidation_Success() {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_CAMPAIGN_REGISTER);
        boolean isValid = campaignService.validation(apiResponse, validPayload);

        assertTrue(isValid);
        assertEquals(HttpStatus.OK, apiResponse.getResponseCode());
        assertEquals(Constants.SUCCESS, apiResponse.getParams().getStatus());
    }

    @Test
    void testValidation_Failure_UpdatesApiResponse() {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_CAMPAIGN_REGISTER);
        validPayload.put("campaignId", "Invalid Campaign");

        boolean isValid = campaignService.validation(apiResponse, validPayload);

        assertFalse(isValid);
        assertEquals(HttpStatus.BAD_REQUEST, apiResponse.getResponseCode());
        assertEquals(Constants.FAILED, apiResponse.getParams().getStatus());
        assertEquals("Invalid campaignid. Only 'Seva Bhav' is accepted", apiResponse.getParams().getErrMsg());
    }
}
