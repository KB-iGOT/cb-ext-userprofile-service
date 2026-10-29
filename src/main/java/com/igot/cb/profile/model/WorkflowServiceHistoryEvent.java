package com.igot.cb.profile.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one element of the 2-element service-history approval event array
 * published by sunbird-cb-workflow on an org-transfer/designation-change approval.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowServiceHistoryEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("source")
    private String source;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("wfId")
    private String wfId;

    @JsonProperty("approvalType")
    private String approvalType;

    @JsonProperty("requestType")
    private String requestType;

    @JsonProperty("entryType")
    private String entryType;

    @JsonProperty("designation")
    private String designation;

    @JsonProperty("orgName")
    private String orgName;

    @JsonProperty("orgId")
    private String orgId;
}
