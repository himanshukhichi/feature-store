package com.example.featurestore.repository;

import com.example.featurestore.model.FeatureStatisticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeatureStatisticsRepository extends JpaRepository<FeatureStatisticsEntity, Long> {

    @Query("""
            select fs
            from FeatureStatisticsEntity fs
            where fs.featureGroup = :featureGroup
              and fs.computedAt = (
                  select max(fs2.computedAt)
                  from FeatureStatisticsEntity fs2
                  where fs2.featureGroup = fs.featureGroup
              )
            order by fs.featureName asc
            """)
    List<FeatureStatisticsEntity> findLatestByFeatureGroup(@Param("featureGroup") String featureGroup);
}
