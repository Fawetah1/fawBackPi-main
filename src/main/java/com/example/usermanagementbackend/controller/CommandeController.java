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
    public ResponseEntity<?> createCommande(@RequestBody Map<String, Object> payload) {
        try {
            System.out.println("Received payload: " + payload);

            // Extract basic commande info
            String clientNom = payload.get("clientNom") != null ? (String) payload.get("clientNom") : "";
            String status = payload.get("status") != null ? (String) payload.get("status") : "PENDING";
            String adresse = payload.get("adresse") != null ? (String) payload.get("adresse") : "";
            String telephone = payload.get("telephone") != null ? (String) payload.get("telephone") : "";
            String gouvernement = payload.get("gouvernement") != null ? (String) payload.get("gouvernement") : "";

            // Extract livreurId if provided
            Long livreurId;
            if (payload.get("livreurId") != null) {
                livreurId = Long.valueOf(payload.get("livreurId").toString());
            } else {
                livreurId = null;
            }

            // Handle user data - use from payload if available, otherwise default to a simple userId
            Long userId = null;
            Map<String, Object> userMap = (Map<String, Object>) payload.get("user");
            if (userMap != null && userMap.get("id") != null) {
                userId = Long.valueOf(userMap.get("id").toString());
            } else {
                userId = 1L; // Default user ID if not provided
            }

            // Try different table names to find the correct one for this database
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int result = 0;
            Long generatedId = null;

            try {
                // Second attempt with table name 'commandes' - plural form (more likely to be the correct one)
                Long finalUserId = userId;
                result = jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO commandes (client_nom, statut, adresse, telephone, gouvernement, user_id, livreur_id) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS
                    );
                    ps.setString(1, clientNom);
                    ps.setString(2, status);
                    ps.setString(3, adresse);
                    ps.setString(4, telephone);
                    ps.setString(5, gouvernement);
                    ps.setLong(6, finalUserId);

                    if (livreurId != null) {
                        ps.setLong(7, livreurId);
                    } else {
                        ps.setNull(7, Types.BIGINT);
                    }

                    return ps;
                }, keyHolder);

                generatedId = keyHolder.getKey().longValue();
                System.out.println("Insert successful with table name 'commandes', generated ID: " + generatedId);
            } catch (Exception e2) {
                System.out.println("Direct SQL insert failed: " + e2.getMessage());

                // Fall back to using the service if direct SQL attempt fails
                System.out.println("Falling back to service-based approach");
                Commande commande = new Commande();
                commande.setClientNom(clientNom);
                try {
                    commande.setStatus(Commande.OrderStatus.valueOf(status));
                } catch (Exception e) {
                    commande.setStatus(Commande.OrderStatus.PENDING);
                }
                commande.setAdresse(adresse);
                commande.setTelephone(telephone);
                commande.setGouvernement(gouvernement);

                // Set up user relationship
                User user = new User();
                user.setId(userId);
                commande.setUser(user);

                // Set livreurId if provided
                if (livreurId != null) {
                    commande.setLivreurId(livreurId);
                }

                // Process lignes commandes from the request payload
                List<LigneCommande> lignesCommande = new ArrayList<>();
                List<Map<String, Object>> lignesCommandeData = (List<Map<String, Object>>) payload.get("lignesCommande");

                if (lignesCommandeData != null && !lignesCommandeData.isEmpty()) {
                    for (Map<String, Object> ligneData : lignesCommandeData) {
                        LigneCommande ligneCommande = new LigneCommande();

                        // Set commande relationship
                        ligneCommande.setCommande(commande);

                        // Extract product information
                        Map<String, Object> produitMap = (Map<String, Object>) ligneData.get("produit");
                        if (produitMap != null && produitMap.get("id") != null) {
                            Long produitId = Long.valueOf(produitMap.get("id").toString());

                            // Create product reference
                            Produit produit = new Produit();
                            produit.setId(produitId);
                            ligneCommande.setProduit(produit);
                        }

                        // Set quantities and prices
                        int qte = ligneData.get("qte") != null ? ((Number)ligneData.get("qte")).intValue() : 1;

                        // Add explicit logging to debug prix_unitaire issues
                        System.out.println("Processing prix_unitaire from JSON: " + ligneData.get("prixUnitaire"));

                        double prixUnitaire = 0.0;
                        if (ligneData.get("prixUnitaire") != null) {
                            try {
                                prixUnitaire = ((Number)ligneData.get("prixUnitaire")).doubleValue();
                                System.out.println("Converted prixUnitaire to: " + prixUnitaire);
                            } catch (Exception ex1) {
                                System.out.println("Error converting prixUnitaire: " + ex1.getMessage());
                                // Try alternate parsing approaches
                                try {
                                    prixUnitaire = Double.parseDouble(ligneData.get("prixUnitaire").toString());
                                    System.out.println("Alternate conversion succeeded: " + prixUnitaire);
                                } catch (Exception ex2) {
                                    System.out.println("Alternate conversion also failed: " + ex2.getMessage());
                                }
                            }
                        }

                        ligneCommande.setQte(qte);
                        ligneCommande.setPrixUnitaire(prixUnitaire);

                        if (ligneData.get("total") != null) {
                            ligneCommande.setTotal(((Number)ligneData.get("total")).doubleValue());
                        } else if (ligneData.get("prixUnitaire") != null) {
                            // Calculate total if not provided
                            try {
                                double prixUnitaireValue = prixUnitaire; // Use the already converted value
                                int qteValue = ligneCommande.getQte();
                                ligneCommande.setTotal(prixUnitaireValue * qteValue);
                                System.out.println("Calculated total: " + (prixUnitaireValue * qteValue) +
                                        " from prix: " + prixUnitaireValue + " and qte: " + qteValue);
                            } catch (Exception ex) {
                                System.out.println("Error calculating total: " + ex.getMessage());
                                ligneCommande.setTotal(0.0); // Default to zero if calculation fails
                            }
                        }

                        // Set TTC if provided
                        if (ligneData.get("ttc") != null) {
                            ligneCommande.setTtc(((Number)ligneData.get("ttc")).doubleValue());
                        }

                        // Add to collection
                        lignesCommande.add(ligneCommande);
                    }
                }

                // Set the lignes commande collection
                commande.setLignesCommande(lignesCommande);

                // Save using service
                Commande savedCommande = commandeService.saveCommande(commande);
                System.out.println("Saved commande using service: " + savedCommande);
                generatedId = savedCommande.getId();
            }

            // If the commande was inserted successfully with a generated ID, insert ligne commande items
            if (generatedId != null && keyHolder.getKey() != null) {
                // Process lignes commandes from the request payload
                List<Map<String, Object>> lignesCommandeData = (List<Map<String, Object>>) payload.get("lignesCommande");

                if (lignesCommandeData != null && !lignesCommandeData.isEmpty()) {
                    for (Map<String, Object> ligneData : lignesCommandeData) {
                        try {
                            // Extract product ID
                            Long produitId = null;
                            Map<String, Object> produitMap = (Map<String, Object>) ligneData.get("produit");
                            if (produitMap != null && produitMap.get("id") != null) {
                                produitId = Long.valueOf(produitMap.get("id").toString());
                            } else {
                                produitId = 1L; // Default product ID
                            }

                            // Extract quantities and prices
                            int qte = ligneData.get("qte") != null ? ((Number)ligneData.get("qte")).intValue() : 1;

                            // Add explicit logging to debug prix_unitaire issues
                            System.out.println("Processing prix_unitaire from JSON: " + ligneData.get("prixUnitaire"));

                            double prixUnitaire = 0.0;
                            if (ligneData.get("prixUnitaire") != null) {
                                try {
                                    prixUnitaire = ((Number)ligneData.get("prixUnitaire")).doubleValue();
                                    System.out.println("Converted prixUnitaire to: " + prixUnitaire);
                                } catch (Exception ex1) {
                                    System.out.println("Error converting prixUnitaire: " + ex1.getMessage());
                                    // Try alternate parsing approaches
                                    try {
                                        prixUnitaire = Double.parseDouble(ligneData.get("prixUnitaire").toString());
                                        System.out.println("Alternate conversion succeeded: " + prixUnitaire);
                                    } catch (Exception ex2) {
                                        System.out.println("Alternate conversion also failed: " + ex2.getMessage());
                                    }
                                }
                            }

                            double total = ligneData.get("total") != null ?
                                    ((Number)ligneData.get("total")).doubleValue() : (prixUnitaire * qte);
                            double ttc = ligneData.get("ttc") != null ?
                                    ((Number)ligneData.get("ttc")).doubleValue() : total;

                            // Add more logging about the SQL insertion
                            System.out.println("Inserting ligne_commande with values: ");
                            System.out.println("commande_id: " + generatedId);
                            System.out.println("produit_id: " + produitId);
                            System.out.println("qte: " + qte);
                            System.out.println("prix_unitaire: " + prixUnitaire);
                            System.out.println("total: " + total);
                            System.out.println("ttc: " + ttc);

                            // Insert ligne commande
                            int rowsInserted = jdbcTemplate.update(
                                    "INSERT INTO lignes_commande (commande_id, produit_id, qte, prix_unitaire, total, ttc) " +
                                            "VALUES (?, ?, ?, ?, ?, ?)",
                                    generatedId, produitId, qte, prixUnitaire, total, ttc
                            );

                            System.out.println("Inserted ligne commande for commande ID: " + generatedId + ", rows affected: " + rowsInserted);
                        } catch (Exception e) {
                            System.out.println("Error inserting ligne commande: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }

            // Create response DTO
            CommandeDTO responseDTO = new CommandeDTO();
            responseDTO.setId(generatedId);
            responseDTO.setClientNom(clientNom);
            responseDTO.setStatus(status);
            responseDTO.setAdresse(adresse);
            responseDTO.setTelephone(telephone);
            if (livreurId != null) {
                responseDTO.setLivreurId(livreurId);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
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

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error creating commande: " + e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommandeDTO> updateCommande(@PathVariable Long id, @RequestBody CommandeDTO commandeDTO) {
        try {
            int rowsAffected = jdbcTemplate.update(
                    "UPDATE commandes SET client_nom = ?, statut = ?, adresse = ?, telephone = ?, livreur_id = ? WHERE id = ?",
                    commandeDTO.getClientNom(),
                    commandeDTO.getStatus(),
                    commandeDTO.getAdresse(),
                    commandeDTO.getTelephone(),
                    commandeDTO.getLivreurId(),
                    id
            );
            if (rowsAffected == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande not found with id: " + id);
            }
            return ResponseEntity.ok(commandeDTO);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error updating commande", e);
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