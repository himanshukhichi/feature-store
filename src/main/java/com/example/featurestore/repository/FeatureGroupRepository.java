package com.example.featurestore.repository;

import com.example.featurestore.model.FeatureGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureGroupRepository extends JpaRepository<FeatureGroupEntity, Long> {
    Optional<FeatureGroupEntity> findByName(String name);

    boolean existsByName(String name);
}
