package com.igot.cb.transactional.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testGettersAndSetters() {
        Config config = new Config();
        String sender = "test@example.com";
        String topic = "Test Topic";
        String otp = "123456";
        String subject = "Test Subject";
        config.setSender(sender);
        config.setTopic(topic);
        config.setOtp(otp);
        config.setSubject(subject);
        assertEquals(sender, config.getSender());
        assertEquals(topic, config.getTopic());
        assertEquals(otp, config.getOtp());
        assertEquals(subject, config.getSubject());
    }

    @Test
    void testNullValues() {
        Config config = new Config();
        assertNull(config.getSender());
        assertNull(config.getTopic());
        assertNull(config.getOtp());
        assertNull(config.getSubject());
    }

    @Test
    void testObjectTypeFields() {
        Config config = new Config();
        Map<String, String> topicMap = Map.of("name", "Test Topic", "id", "12345");
        Map<String, Object> otpMap = Map.of("value", "123456", "expiry", 300);
        config.setTopic(topicMap);
        config.setOtp(otpMap);
        assertEquals(topicMap, config.getTopic());
        assertEquals(otpMap, config.getOtp());
    }
}