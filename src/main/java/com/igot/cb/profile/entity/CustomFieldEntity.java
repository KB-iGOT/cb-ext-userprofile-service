package com.igot.cb.profile.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "custom_fields")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class CustomFieldEntity {
    @Id
    private String customFiledId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode customFieldData;

    private Boolean isMandatory;

    private Boolean isActive;

    private Timestamp createdOn;

    private Timestamp updatedOn;
}
