package com.example.usermanagementbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandeDTO {
    private Long id;
    private String clientNom;
    private String status;
    private String adresse;
    private String telephone;
    private String statusColor;
    private LivreurDTO livreur;
    private Long livreurId;

    public CommandeDTO(Long id, String clientNom, String statut, String address, String telephone) {
        this.id = id;
        this.clientNom = clientNom;
        this.status = statut;
        this.adresse = address;
        this.telephone = telephone;
    }
}