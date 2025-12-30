package com.igot.cb.masterdata.repository;

import com.igot.cb.masterdata.model.Degree;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DegreeRepository extends JpaRepository<Degree, Long> {
    // Search by name, level, or duration (case-insensitive)
    Page<Degree> findByNameContainingIgnoreCaseAndStatus(
            String name, int status, Pageable pageable);
    Page<Degree> findByStatus(int status, Pageable pageable);
    boolean existsByNameIgnoreCase(String name);
    Optional<Degree> findByNameIgnoreCase(String name);
}

