package com.example.usermanagementbackend.repository;

import com.example.usermanagementbackend.entity.LigneFacture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LigneFactureRepository extends JpaRepository<LigneFacture, Long> {
    @Query("SELECT lf FROM LigneFacture lf JOIN FETCH lf.produit WHERE lf.facture.id = :factureId")
    List<LigneFacture> findByFactureIdWithProduit(@Param("factureId") Long factureId);
}