package com.example.usermanagementbackend.repository;

import com.example.usermanagementbackend.entity.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FactureRepository extends JpaRepository<Facture, Long> {

    @Query("SELECT f FROM Facture f " +
            "JOIN FETCH f.commande c " +
            "JOIN FETCH c.transactions t " +
            "JOIN FETCH c.user u " +
            "LEFT JOIN FETCH f.lignesFacture lf " +
            "LEFT JOIN FETCH lf.produit p " +
            "WHERE t.id = :transactionId")
    List<Facture> findByTransactionId(@Param("transactionId") Long transactionId);
}