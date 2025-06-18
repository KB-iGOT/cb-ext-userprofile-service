package com.igot.cb.profile.repository;


import com.igot.cb.profile.entity.CustomFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomFieldRepository extends JpaRepository<CustomFieldEntity, String> {
    Optional<CustomFieldEntity> findByCustomFiledIdAndIsActiveTrue(String customFiledId);
}
