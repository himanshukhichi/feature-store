package com.example.featurestore.repository;

import com.example.featurestore.model.FeatureGroupVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureGroupVersionRepository extends JpaRepository<FeatureGroupVersionEntity, Long> {
    List<FeatureGroupVersionEntity> findByFeatureGroupNameOrderByVersionDesc(String featureGroupName);

    Optional<FeatureGroupVersionEntity> findByFeatureGroupNameAndVersion(String featureGroupName, int version);
}
