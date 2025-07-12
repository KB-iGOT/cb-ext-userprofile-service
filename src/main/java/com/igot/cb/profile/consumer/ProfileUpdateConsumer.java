package com.igot.cb.profile.consumer;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProfileUpdateConsumer {
    private ProfileServiceImpl profileService;
    private ProjectUtil projectUtil;

    public ProfileUpdateConsumer(ProfileServiceImpl profileService, ProjectUtil projectUtil) {
        this.profileService = profileService;
        this.projectUtil = projectUtil;
    }

    @KafkaListener(topics = "${kafka.topic.name.user.profile.update}", groupId = "${kafka.group.name.user.profile.update}")
    public void userProfileUpdateConsumer(ConsumerRecord<String, String> data) throws IOException {
        try {
            log.info("ProfileUpdateConsumer::userProfileUpdated:topic name: {} and recievedData: {}", data.topic(),
                    data.value());
            if (StringUtils.hasText(data.value())) {
                Map<String, Object> userData = projectUtil.parseMap(data.value());
                if (MapUtils.isNotEmpty(userData)) {
                    CompletableFuture.runAsync(() ->
                    // Fetch user data from DB and update cache
                    profileService.readUserDataFromDB((String) userData.get(Constants.USER_ID), null));
                } else {
                    log.error("Error in userProfileUpdated: Invalid userData in Kafka Msg");
                }
            } else {
                log.error("Error in userProfileUpdated: Invalid Kafka Msg");
            }
        } catch (Exception e) {
            log.error("Error while processing the kafka event {} : ", data, e);
        }
    }
}
