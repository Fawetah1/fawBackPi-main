package com.example.usermanagementbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"lignesCommande", "transactions", "factures"})
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_nom")
    private String clientNom;

    private Double total;


    private LocalDate dateCreation;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "gouvernement")
    private String gouvernement;

    @Column(name = "adresse")
    private String adresse;

    private OrderStatus status; // Changed to OrderStatus enum
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User is required for a commande")
    @JsonIgnoreProperties("commandes")
    private User user;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"commande", "produit.ligneCommandes"})
    private List<LigneCommande> lignesCommande = new ArrayList<>();

    @ManyToMany(mappedBy = "commandes", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("commandes")
    private Set<TransactionPaiement> transactions = new HashSet<>();

    @JsonIgnoreProperties("commande")
    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL)
    private List<Facture> factures = new ArrayList<>();

    @Column(name = "livreur_id")
    private Long livreurId;

    public enum OrderStatus {
        PENDING, PENDING_PAYMENT, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, PAID, EN_COURS, NON_LIVRE, LIVRE,
    }
}