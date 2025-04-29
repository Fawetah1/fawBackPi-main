package com.example.usermanagementbackend.repository;

import com.example.usermanagementbackend.entity.TransactionPaiement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionPaiementRepository extends JpaRepository<TransactionPaiement, Long> {

    // Find by payment intent ID (for Stripe)
    Optional<TransactionPaiement> findByPaymentIntentId(String paymentIntentId);

    // Find transactions by date range
    @Query("SELECT t FROM TransactionPaiement t WHERE t.dateTransaction BETWEEN :start AND :end")
    List<TransactionPaiement> findByDateRange(LocalDateTime start, LocalDateTime end);

    // Fetch transaction with associated commandes
    @EntityGraph(attributePaths = {"commandes"})
    @Query("SELECT t FROM TransactionPaiement t WHERE t.id = :id")
    Optional<TransactionPaiement> findByIdWithCommandes(Long id);
}