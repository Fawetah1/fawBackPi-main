package com.example.usermanagementbackend.repository;

import com.example.usermanagementbackend.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    Optional<Recommendation> findByUserId(Long userId);
}