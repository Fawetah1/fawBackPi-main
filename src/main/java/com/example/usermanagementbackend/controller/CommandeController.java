package com.example.usermanagementbackend.controller;

import com.example.usermanagementbackend.dto.CommandeDTO;
import com.example.usermanagementbackend.dto.LivreurDTO;
import com.example.usermanagementbackend.entity.Commande;
import com.example.usermanagementbackend.entity.LigneCommande;
import com.example.usermanagementbackend.entity.Produit;
import com.example.usermanagementbackend.entity.User;
import com.example.usermanagementbackend.service.CommandeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/commandes")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class CommandeController {

    private final JdbcTemplate jdbcTemplate;
    private final CommandeService commandeService;

    @GetMapping
    public ResponseEntity<?> getAllCommandes() {
        try {
            // First, log the exception details to help debug the issue
            System.out.println("===== Running getAllCommandes =====");
            
            try {
                // Attempt to get the table structure to verify it exists
                List<String> tableNames = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", 
                    String.class
                );
                System.out.println("Available tables: " + tableNames);
            } catch (Exception ex) {
                System.out.println("Error checking table structure: " + ex.getMessage());
            }
            
            // Fallback to the service implementation which might be more stable
            List<Commande> commandeList = commandeService.getAllCommandes();
            
            // Convert entities to DTOs manually
            List<CommandeDTO> commandes = new ArrayList<>();
            for (Commande commande : commandeList) {
                CommandeDTO dto = new CommandeDTO();
                dto.setId(commande.getId());
                dto.setClientNom(commande.getClientNom());
                dto.setStatus(commande.getStatus() != null ? commande.getStatus().toString() : "PENDING");
                dto.setAdresse(commande.getAdresse());
                dto.setTelephone(commande.getTelephone());
                
                // Handle livreur if present
                if (commande.getLivreurId() != null) {
                    dto.setLivreurId(commande.getLivreurId());
                }
                
                commandes.add(dto);
            }
            
            return ResponseEntity.ok(commandes);
        } catch (Exception e) {
            e.printStackTrace();
            // Return a more user-friendly response instead of throwing an exception
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error fetching commandes: " + e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommandeDTO> getCommandeById(@PathVariable Long id) {
        try {
            CommandeDTO cmd = jdbcTemplate.queryForObject(
                    "SELECT c.id, c.client_nom, c.statut as status, c.adresse, c.telephone, c.livreur_id, " +
                            "lvr.nom as livreur_nom, lvr.email as livreur_email, lvr.telephone as livreur_telephone, lvr.user_id as livreur_user_id " +
                            "FROM commandes c " +
                            "LEFT JOIN livreurs lvr ON c.livreur_id = lvr.id " +
                            "WHERE c.id = ?",
                    (rs, rowNum) -> {
                        CommandeDTO commandeDTO = new CommandeDTO(
                                rs.getLong("id"),
                                rs.getString("client_nom"),
                                rs.getString("status"),
                                rs.getString("adresse"),
                                rs.getString("telephone")
                        );
                        commandeDTO.setLivreurId(rs.getLong("livreur_id"));
                        if (!rs.wasNull()) {
                            LivreurDTO livreur = new LivreurDTO();
                            livreur.setId(rs.getLong("livreur_id"));
                            livreur.setNom(rs.getString("livreur_nom"));
                            livreur.setEmail(rs.getString("livreur_email"));
                            livreur.setTelephone(rs.getString("livreur_telephone"));
                            livreur.setUserId(rs.getLong("livreur_user_id"));
                            commandeDTO.setLivreur(livreur);
                        }
                        return commandeDTO;
                    },
                    id
            );
            return ResponseEntity.ok(cmd);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande not found with id: " + id);
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getCommandesByStatus(@PathVariable Commande.OrderStatus status) {
        try {
            List<Commande> commandes = commandeService.getCommandesByStatus(status);
            return ResponseEntity.ok(commandes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching commandes with status " + status + ": " + e.getMessage());
        }
    }

    @GetMapping("/date-range")
    public ResponseEntity<?> getCommandesByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        try {
            List<Commande> commandes = commandeService.getCommandesByDateRange(startDate, endDate);
            return ResponseEntity.ok(commandes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching commandes between " + startDate + " and " + endDate + ": " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Commande>> getCommandesByUser(@PathVariable Long userId) {
        try {
            List<Commande> commandes = commandeService.getCommandesByUser(userId);
            return ResponseEntity.ok(commandes != null ? commandes : Collections.emptyList());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList()); // Always return an empty array on error
        }
    }

    @GetMapping("/user/{userId}/pending")
    public ResponseEntity<?> getPendingCommandesByUser(@PathVariable Long userId) {
        try {
            List<Commande> commandes = commandeService.getPendingCommandesByUser(userId);
            return ResponseEntity.ok(commandes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching pending commandes for user " + userId + ": " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createCommande(@Valid @RequestBody Commande commande) {
        try {
            System.out.println("Received commande: " + commande);

            // Ensure default values and validate input
            if (commande.getClientNom() == null || commande.getClientNom().trim().isEmpty()) {
                System.out.println("ClientNom is null or empty, setting default");
                commande.setClientNom("");
            }
            if (commande.getStatus() == null) {
                System.out.println("Status is null, setting to PENDING");
                commande.setStatus(Commande.OrderStatus.PENDING);
            }
            if (commande.getAdresse() == null || commande.getAdresse().trim().isEmpty()) {
                System.out.println("Adresse is null or empty, setting default");
                commande.setAdresse("");
            }
            if (commande.getTelephone() == null || commande.getTelephone().trim().isEmpty()) {
                System.out.println("Telephone is null or empty, setting default");
                commande.setTelephone("");
            }
            if (commande.getGouvernement() == null || commande.getGouvernement().trim().isEmpty()) {
                System.out.println("Gouvernement is null or empty, setting default");
                commande.setGouvernement("");
            }
            if (commande.getUser() == null || commande.getUser().getId() == null) {
                System.out.println("User is null or has no ID, setting default user ID");
                User user = new User();
                user.setId(1L); // Default user ID
                commande.setUser(user);
            }

            // Process lignesCommande (if needed, though CommandeService already handles this)
            if (commande.getLignesCommande() != null && !commande.getLignesCommande().isEmpty()) {
                for (LigneCommande ligne : commande.getLignesCommande()) {
                    System.out.println("Processing ligneCommande: " + ligne);
                    if (ligne.getProduit() == null || ligne.getProduit().getId() == null) {
                        throw new IllegalArgumentException("Produit is required for ligneCommande");
                    }
                    if (ligne.getQte() <= 0) {
                        ligne.setQte(1); // Default quantity
                        System.out.println("Qte is invalid, set to 1");
                    }
                    if (ligne.getPrixUnitaire() <= 0) {
                        System.out.println("PrixUnitaire is invalid, will be set by CommandeService");
                    }
                    ligne.setCommande(commande);
                }
            } else {
                throw new IllegalArgumentException("LignesCommande cannot be null or empty");
            }

            // Save using service
            System.out.println("Saving commande using CommandeService");
            Commande savedCommande = commandeService.saveCommande(commande);
            System.out.println("Commande saved successfully: " + savedCommande);

            // Create response DTO
            CommandeDTO responseDTO = new CommandeDTO();
            responseDTO.setId(savedCommande.getId());
            responseDTO.setClientNom(savedCommande.getClientNom());
            responseDTO.setStatus(savedCommande.getStatus() != null ? savedCommande.getStatus().toString() : "PENDING");
            responseDTO.setAdresse(savedCommande.getAdresse());
            responseDTO.setTelephone(savedCommande.getTelephone());
            responseDTO.setLivreurId(savedCommande.getLivreurId());

            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
        } catch (IllegalArgumentException e) {
            System.out.println("Validation error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Validation error: " + e.getMessage(), "errorType", e.getClass().getSimpleName()));
        } catch (Exception e) {
            System.out.println("======= ERROR CREATING COMMANDE =======");
            System.out.println("Error message: " + e.getMessage());
            System.out.println("Error class: " + e.getClass().getName());
            System.out.println("Stack trace:");
            e.printStackTrace();
            if (e.getCause() != null) {
                System.out.println("Cause: " + e.getCause().getMessage());
                e.getCause().printStackTrace();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error creating commande: " + e.getMessage(), "errorType", e.getClass().getSimpleName()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCommande(@PathVariable Long id, @RequestBody Commande commande) {
        try {
            Commande updatedCommande = commandeService.updateCommande(id, commande);
            return ResponseEntity.ok(updatedCommande);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating commande with id " + id + ": " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCommande(@PathVariable Long id) {
        try {
            System.out.println("Attempting to delete commande with id: " + id);
            
            // Try with both possible table names
            int rowsAffected = 0;
            try {
                // First attempt with 'commande' (singular)
                rowsAffected = jdbcTemplate.update("DELETE FROM commande WHERE id = ?", id);
                System.out.println("Delete with 'commande' affected " + rowsAffected + " rows");
            } catch (Exception e1) {
                System.out.println("First delete attempt failed: " + e1.getMessage());
                
                try {
                    // Second attempt with 'commandes' (plural)
                    rowsAffected = jdbcTemplate.update("DELETE FROM commandes WHERE id = ?", id);
                    System.out.println("Delete with 'commandes' affected " + rowsAffected + " rows");
                } catch (Exception e2) {
                    System.out.println("Second delete attempt failed: " + e2.getMessage());
                    
                    // Final fallback to service layer
                    try {
                        commandeService.deleteCommande(id);
                        System.out.println("Delete using service layer succeeded");
                        rowsAffected = 1; // Assume success if no exception
                    } catch (Exception e3) {
                        System.out.println("Service layer delete failed: " + e3.getMessage());
                        throw e3; // Re-throw to be caught by outer catch
                    }
                }
            }
            
            if (rowsAffected == 0) {
                System.out.println("No rows affected, commande not found");
                return ResponseEntity.notFound().build();
            }
            
            System.out.println("Delete successful");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error deleting commande with id " + id + ": " + e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<?> checkoutCommande(@PathVariable Long id) {
        try {
            int rowsAffected = jdbcTemplate.update(
                    "UPDATE commandes SET statut = 'PAID' WHERE id = ? AND (statut = 'PENDING' OR statut = 'PENDING_PAYMENT')",
                    id
            );
            if (rowsAffected == 0) {
                throw new IllegalStateException("Commande not found or must be in PENDING or PENDING_PAYMENT status to checkout");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error checking out commande with id " + id + ": " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}