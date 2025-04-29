package com.example.usermanagementbackend.service;

import com.example.usermanagementbackend.entity.*;
import com.example.usermanagementbackend.repository.*;
import com.example.usermanagementbackend.enums.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final PurchaseRepository purchaseRepository;
    private final ProduitRepository produitRepository;
    private final RecommendationRepository recommendationRepository;

    @Transactional
    public List<Produit> getRecommendedProducts(Long userId, int limit) {
        // 1. Récupérer l'historique d'achats
        List<Purchase> userPurchases = purchaseRepository.findByUserId(userId);

        // 2. Extraire les catégories achetées
        Set<Category> purchasedCategories = userPurchases.stream()
                .flatMap(p -> p.getProduits().stream())
                .map(Produit::getCategory)
                .collect(Collectors.toSet());

        // 3. Produits fréquemment achetés
        List<Produit> frequentlyBought = getFrequentlyBoughtProducts(userPurchases);

        // 4. Produits similaires
        List<Produit> similarProducts = getSimilarProducts(purchasedCategories, frequentlyBought, limit);

        // 5. Combiner et filtrer
        return combineRecommendations(frequentlyBought, similarProducts, limit);
    }

    private List<Produit> getFrequentlyBoughtProducts(List<Purchase> purchases) {
        Map<Produit, Long> productCounts = purchases.stream()
                .flatMap(p -> p.getProduits().stream())
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

        return productCounts.entrySet().stream()
                .sorted(Map.Entry.<Produit, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<Produit> getSimilarProducts(Set<Category> categories, List<Produit> exclude, int limit) {
        if (categories.isEmpty()) {
            return produitRepository.findTopByStockGreaterThanOrderBySalesCountDesc(
                    0, PageRequest.of(0, limit));
        }

        return produitRepository.findByCategoryInAndIdNotInAndStockGreaterThan(
                categories,
                exclude.stream().map(Produit::getId).collect(Collectors.toList()),
                0,
                PageRequest.of(0, limit)
        );
    }

    private List<Produit> combineRecommendations(List<Produit> primary, List<Produit> secondary, int limit) {
        List<Produit> combined = new ArrayList<>();
        combined.addAll(primary);
        combined.addAll(secondary);

        return combined.stream()
                .distinct()
                .filter(p -> p.getStock() > 0)
                .limit(limit)
                .collect(Collectors.toList());
    }
}