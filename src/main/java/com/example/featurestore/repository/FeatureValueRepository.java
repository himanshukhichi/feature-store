package com.example.featurestore.repository;

import com.example.featurestore.model.FeatureValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FeatureValueRepository extends JpaRepository<FeatureValueEntity, Long> {

    @Query("""
            select fv
            from FeatureValueEntity fv
            where fv.featureGroup = :featureGroup
              and fv.entityId = :entityId
              and fv.eventTime <= :asOf
              and fv.eventTime = (
                  select max(fv2.eventTime)
                  from FeatureValueEntity fv2
                  where fv2.featureGroup = fv.featureGroup
                    and fv2.entityId = fv.entityId
                    and fv2.featureName = fv.featureName
                    and fv2.eventTime <= :asOf
              )
            """)
    List<FeatureValueEntity> findLatestValuesAsOf(
            @Param("featureGroup") String featureGroup,
            @Param("entityId") String entityId,
            @Param("asOf") Instant asOf
    );

    List<FeatureValueEntity> findByFeatureGroup(String featureGroup);
}
