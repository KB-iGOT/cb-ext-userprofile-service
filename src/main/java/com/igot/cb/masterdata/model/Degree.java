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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "degree_seq")
    @SequenceGenerator(name = "degree_seq", sequenceName = "degree_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "description")
    private String description;
    @Column(name = "status")
    private int status;
    @Column(name = "added_on")
    private LocalDateTime addedOn;
    @Column(name = "updated_on")
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

