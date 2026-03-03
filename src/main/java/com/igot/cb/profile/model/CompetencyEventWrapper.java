package com.igot.cb.profile.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper class for Competency Acquired events
 * Wraps the actual event data in an "edata" field as per Kafka message structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetencyEventWrapper {

    @JsonProperty("edata")
    private CompetencyAcquiredEvent edata;
}

