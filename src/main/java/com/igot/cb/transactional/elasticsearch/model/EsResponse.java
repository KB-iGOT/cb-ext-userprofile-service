package com.igot.cb.transactional.elasticsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsResponse {

    private boolean success;
    private String message;
    private String errorCode;
    private String documentId;
    private Long count;
    private Object data;

    @Builder.Default
    private Instant timestamp = Instant.now();  // auto timestamp
}