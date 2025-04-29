package com.example.usermanagementbackend.controller;

import com.example.usermanagementbackend.entity.Produit;
import com.example.usermanagementbackend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendationService;

    @GetMapping
    public List<Produit> getRecommendedProducts(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "6") int limit) {

        return recommendationService.getRecommendedProducts(userId, limit);
    }
}