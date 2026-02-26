package com.igot.cb.profile.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Competency Acquired event to be published to Kafka
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetencyAcquiredEvent {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("contentId")
    private String contentId;

    @JsonProperty("batchId")
    private String batchId;

    @JsonProperty("contextType")
    private String contextType;
}

