package com.example.usermanagementbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.*;

@Entity
@Data
public class Recommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "recommendation_product",
            joinColumns = @JoinColumn(name = "recommendation_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private List<Produit> recommendedProducts = new ArrayList<>();

    @Column(nullable = false)
    private Date lastUpdated = new Date();
}