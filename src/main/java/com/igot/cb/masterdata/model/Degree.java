package com.igot.cb.masterdata.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "degree")
public class Degree {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
    private String description;
    private int status;
    private LocalDateTime addedOn;
    private LocalDateTime updatedOn;

    @PrePersist
    protected void onCreate() {
        this.addedOn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedOn = LocalDateTime.now();
    }
}

