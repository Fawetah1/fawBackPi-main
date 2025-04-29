package com.example.usermanagementbackend.service;

import com.example.usermanagementbackend.entity.Commande;
import com.example.usermanagementbackend.entity.LigneCommande;
import com.example.usermanagementbackend.entity.Commande.OrderStatus;
import com.example.usermanagementbackend.entity.Produit;
import com.example.usermanagementbackend.entity.User;
import com.example.usermanagementbackend.repository.CommandeRepository;
import com.example.usermanagementbackend.repository.ProduitRepository;
import com.example.usermanagementbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CommandeService {

    private static final Logger logger = LoggerFactory.getLogger(CommandeService.class);

    private final CommandeRepository commandeRepository;
    private final ProduitRepository produitRepository;
    private final UserRepository userRepository;

    private static final List<String> TUNISIAN_GOVERNORATES = Arrays.asList(
            "Ariana", "Beja", "Ben Arous", "Bizerte", "Gabes", "Gafsa", "Jendouba",
            "Kairouan", "Kasserine", "Kebili", "Kef", "Mahdia", "Manouba", "Medenine",
            "Monastir", "Nabeul", "Sfax", "Sidi Bouzid", "Siliana", "Sousse",
            "Tataouine", "Tozeur", "Tunis", "Zaghouan"
    );

    public CommandeService(CommandeRepository commandeRepository,
                           ProduitRepository produitRepository,
                           UserRepository userRepository) {
        this.commandeRepository = commandeRepository;
        this.produitRepository = produitRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Commande> getAllCommandes() {
        logger.info("Fetching all commandes");
        List<Commande> commandes = commandeRepository.findAllWithLignesAndProduits();
        logger.info("Found {} commandes", commandes.size());
        return commandes;
    }

    @Transactional(readOnly = true)
    public Optional<Commande> getCommandeById(Long id) {
        logger.info("Fetching commande with id: {}", id);
        Optional<Commande> commande = commandeRepository.findByIdWithLignesAndProduits(id);
        logger.info("Commande found: {}", commande.isPresent());
        return commande;
    }

    @Transactional(readOnly = true)
    public List<Commande> getCommandesByStatus(OrderStatus status) {
        logger.info("Fetching commandes with status: {}", status);
        List<Commande> commandes = commandeRepository.findByStatusWithLignesAndProduits(status);
        logger.info("Found {} commandes with status {}", commandes.size(), status);
        return commandes;
    }

    @Transactional(readOnly = true)
    public List<Commande> getCommandesByDateRange(LocalDate startDate, LocalDate endDate) {
        logger.info("Fetching commandes between {} and {}", startDate, endDate);
        List<Commande> commandes = commandeRepository.findByDateCreationBetweenWithLignesAndProduits(startDate, endDate);
        logger.info("Found {} commandes in date range", commandes.size());
        return commandes;
    }

    @Transactional(readOnly = true)
    public List<Commande> getCommandesByUser(Long userId) {
        logger.info("Fetching commandes for user id: {}", userId);
        List<Commande> commandes = commandeRepository.findByUserIdWithLignesAndProduits(userId);
        logger.info("Found {} commandes for user id: {}", commandes.size(), userId);
        return commandes;
    }

    @Transactional(readOnly = true)
    public List<Commande> getPendingCommandesByUser(Long userId) {
        logger.info("Fetching pending commandes for user id: {}", userId);
        List<Commande> commandes = commandeRepository.findByUserIdAndStatusInWithLignesAndProduits(
                userId, OrderStatus.PENDING, OrderStatus.PENDING_PAYMENT);
        logger.info("Found {} pending commandes for user id: {}", commandes.size(), userId);
        return commandes;
    }

    @Transactional
    public Commande saveCommande(Commande commande) {
        try {
            logger.info("Starting saveCommande for client: {}", commande.getClientNom());

            // Validate user (already enforced by @NotNull, but adding logging for clarity)
            User user = commande.getUser();
            Long userId = user.getId();
            logger.info("Fetching user with id: {}", userId);
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
            commande.setUser(user);
            logger.info("User found: id={}", user.getId());

            // Set date if null
            if (commande.getDateCreation() == null) {
                logger.info("Setting dateCreation to current date");
                commande.setDateCreation(LocalDate.now());
            }

            // Process lignesCommande
            List<LigneCommande> lignes = commande.getLignesCommande();
            if (lignes == null || lignes.isEmpty()) {
                logger.warn("LignesCommande is null or empty");
                throw new IllegalArgumentException("LignesCommande cannot be null or empty");
            }
            logger.info("Processing {} lignesCommande", lignes.size());
            for (LigneCommande ligne : lignes) {
                logger.info("Processing ligne with qte: {}", ligne.getQte());
                ligne.setCommande(commande);
                Long produitId = ligne.getProduit().getId();
                logger.info("Fetching produit with id: {}", produitId);
                Produit produit = produitRepository.findById(produitId)
                        .orElseThrow(() -> new IllegalArgumentException("Produit not found with id: " + produitId));
                logger.info("Produit found: id={}, nom={}", produit.getId(), produit.getNom());
                ligne.setProduit(produit);
                int qte = ligne.getQte();
                double prix = produit.getPrix();
                ligne.setPrixUnitaire(prix);
                double total = qte * prix;
                double ttc = total * 1.19;
                ligne.setTotal(total);
                ligne.setTtc(ttc);
                logger.info("Ligne updated: qte={}, prixUnitaire={}, total={}, ttc={}", qte, prix, total, ttc);
            }

            // Calculate total
            double totalCommande = lignes.stream().mapToDouble(LigneCommande::getTotal).sum();
            commande.setTotal(totalCommande);
            logger.info("Calculated commande total: {}", totalCommande);

            // Validate commande
            logger.info("Validating commande");
            validateOrder(commande);

            // Save to database
            logger.info("Saving commande to database");
            Commande saved = commandeRepository.save(commande);
            logger.info("Commande saved with id: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            logger.error("Error saving commande: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public Commande updateCommande(Long id, Commande updatedCommande) {
        logger.info("Updating commande with id: {}", id);
        Commande existing = getCommandeById(id)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée avec l'ID: " + id));

        existing.setClientNom(updatedCommande.getClientNom());
        existing.setTotal(updatedCommande.getTotal());
        existing.setUser(updatedCommande.getUser());
        existing.setTelephone(updatedCommande.getTelephone());
        existing.setGouvernement(updatedCommande.getGouvernement());
        existing.setAdresse(updatedCommande.getAdresse());
        existing.setLignesCommande(updatedCommande.getLignesCommande());

        List<LigneCommande> lignes = existing.getLignesCommande();
        if (lignes != null) {
            for (LigneCommande ligne : lignes) {
                Produit produit = produitRepository.findById(ligne.getProduit().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Produit not found with id: " + ligne.getProduit().getId()));
                ligne.setProduit(produit);
                ligne.setPrixUnitaire(produit.getPrix());
                ligne.setTotal(ligne.getQte() * ligne.getPrixUnitaire());
                ligne.setTtc(ligne.getTotal() * 1.19);
            }
            existing.setTotal(lignes.stream().mapToDouble(LigneCommande::getTotal).sum());
        }

        validateOrder(existing);

        if (updatedCommande.getStatus() != null) {
            transitionOrderStatus(existing, updatedCommande.getStatus());
        }

        Commande updated = commandeRepository.save(existing);
        logger.info("Commande updated with id: {}", updated.getId());
        return updated;
    }

    @Transactional
    public void deleteCommande(Long id) {
        logger.info("Deleting commande with id: {}", id);
        Commande commande = getCommandeById(id)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée avec l'ID: " + id));
        if (commande.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Seules les commandes annulées peuvent être supprimées");
        }
        commandeRepository.deleteById(id);
        logger.info("Commande deleted with id: {}", id);
    }

    private void validateOrder(Commande commande) {
        logger.info("Validating commande");
        if (commande.getLignesCommande() == null || commande.getLignesCommande().isEmpty()) {
            logger.error("Validation failed: LignesCommande is null or empty");
            throw new IllegalArgumentException("La commande doit contenir au moins une ligne");
        }

        for (LigneCommande ligne : commande.getLignesCommande()) {
            if (ligne.getProduit() == null || ligne.getProduit().getId() == null) {
                logger.error("Validation failed: Produit is invalid for ligne");
                throw new IllegalArgumentException("Produit invalide pour la ligne");
            }
            Produit produit = produitRepository.findById(ligne.getProduit().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé"));
            if (produit.getStock() < ligne.getQte()) {
                logger.error("Validation failed: Insufficient stock for produit: {}", produit.getNom());
                throw new IllegalArgumentException("Stock insuffisant pour le produit: " + produit.getNom());
            }
        }

        if (commande.getUser() != null && commande.getUser().getId() != null) {
            User user = userRepository.findById(commande.getUser().getId())
                    .orElseThrow(() -> new IllegalArgumentException("User non trouvé"));
            if (user.getCreditLimit() != null && commande.getTotal() > user.getCreditLimit()) {
                logger.error("Validation failed: Total exceeds credit limit: {}", user.getCreditLimit());
                throw new IllegalArgumentException("Le total dépasse la limite de crédit: " + user.getCreditLimit());
            }
        }

        if (commande.getTelephone() != null && !commande.getTelephone().matches("^[0-9]{8}$")) {
            logger.error("Validation failed: Invalid telephone number");
            throw new IllegalArgumentException("Le numéro de téléphone doit contenir exactement 8 chiffres");
        }

        if (commande.getGouvernement() != null && !TUNISIAN_GOVERNORATES.contains(commande.getGouvernement())) {
            logger.error("Validation failed: Invalid gouvernement: {}", commande.getGouvernement());
            throw new IllegalArgumentException("Gouvernorat invalide. Choisissez parmi: " + TUNISIAN_GOVERNORATES);
        }

        if (commande.getGouvernement() != null && (commande.getAdresse() == null || commande.getAdresse().trim().isEmpty())) {
            logger.error("Validation failed: Adresse is required when gouvernement is provided");
            throw new IllegalArgumentException("L'adresse est obligatoire lorsque le gouvernorat est sélectionné");
        }
        logger.info("Commande validation passed");
    }

    @Transactional
    public void transitionOrderStatus(Commande commande, OrderStatus newStatus) {
        logger.info("Transitioning commande status to: {}", newStatus);
        OrderStatus currentStatus = commande.getStatus();
        boolean validTransition = switch (newStatus) {
            case PENDING -> currentStatus == null;
            case PENDING_PAYMENT -> currentStatus == OrderStatus.PENDING;
            case CONFIRMED -> currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.PENDING_PAYMENT;
            case SHIPPED -> currentStatus == OrderStatus.CONFIRMED;
            case DELIVERED -> currentStatus == OrderStatus.SHIPPED;
            case CANCELLED -> currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.PENDING_PAYMENT || currentStatus == OrderStatus.CONFIRMED;
            case PAID -> currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.PENDING_PAYMENT || currentStatus == OrderStatus.CONFIRMED;
            default -> throw new IllegalArgumentException("Statut inconnu: " + newStatus);
        };

        if (!validTransition) {
            logger.error("Invalid status transition from {} to {}", currentStatus, newStatus);
            throw new IllegalArgumentException("Transition de statut invalide de " + currentStatus + " à " + newStatus);
        }

        commande.setStatus(newStatus);
        if (newStatus == OrderStatus.SHIPPED && commande.getLignesCommande() != null) {
            for (LigneCommande ligne : commande.getLignesCommande()) {
                Produit produit = produitRepository.findById(ligne.getProduit().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé"));
                produit.setStock(produit.getStock() - ligne.getQte());
                produitRepository.save(produit);
            }
        }
        commandeRepository.save(commande);
        logger.info("Commande status transitioned to: {}", newStatus);
    }
}