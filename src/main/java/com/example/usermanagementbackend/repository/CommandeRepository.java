package com.example.usermanagementbackend.repository;

import com.example.usermanagementbackend.entity.Commande;
import com.example.usermanagementbackend.entity.Commande.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommandeRepository extends JpaRepository<Commande, Long> {

    @Query("SELECT DISTINCT c FROM Commande c LEFT JOIN FETCH c.lignesCommande l LEFT JOIN FETCH l.produit LEFT JOIN FETCH c.user")
    List<Commande> findAllWithLignesAndProduits();

    @Query("SELECT c FROM Commande c LEFT JOIN FETCH c.lignesCommande l LEFT JOIN FETCH l.produit LEFT JOIN FETCH c.user WHERE c.id = :id")
    Optional<Commande> findByIdWithLignesAndProduits(@Param("id") Long id);

    @Query("SELECT c FROM Commande c LEFT JOIN FETCH c.lignesCommande l LEFT JOIN FETCH l.produit LEFT JOIN FETCH c.user WHERE c.status = :status")
    List<Commande> findByStatusWithLignesAndProduits(@Param("status") OrderStatus status);

    @Query("SELECT c FROM Commande c LEFT JOIN FETCH c.lignesCommande l LEFT JOIN FETCH l.produit LEFT JOIN FETCH c.user WHERE c.dateCreation BETWEEN :startDate AND :endDate")
    List<Commande> findByDateCreationBetweenWithLignesAndProduits(@Param("startDate") LocalDate startDate,
                                                                  @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM Commande c LEFT JOIN FETCH c.lignesCommande l LEFT JOIN FETCH l.produit LEFT JOIN FETCH c.user WHERE c.user.id = :userId")
    List<Commande> findByUserIdWithLignesAndProduits(@Param("userId") Long userId);

    @Query("SELECT c FROM Commande c LEFT JOIN FETCH c.lignesCommande l LEFT JOIN FETCH l.produit LEFT JOIN FETCH c.user WHERE c.user.id = :userId AND c.status IN (:status1, :status2)")
    List<Commande> findByUserIdAndStatusInWithLignesAndProduits(@Param("userId") Long userId,
                                                                @Param("status1") OrderStatus status1,
                                                                @Param("status2") OrderStatus status2);
}