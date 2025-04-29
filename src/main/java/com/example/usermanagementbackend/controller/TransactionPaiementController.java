package com.example.usermanagementbackend.controller;

import com.example.usermanagementbackend.entity.Commande;
import com.example.usermanagementbackend.entity.Facture;
import com.example.usermanagementbackend.entity.LigneFacture;
import com.example.usermanagementbackend.entity.TransactionPaiement;
import com.example.usermanagementbackend.service.CommandeService;
import com.example.usermanagementbackend.service.FactureService;
import com.example.usermanagementbackend.service.TransactionPaiementService;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.exception.StripeException;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "http://localhost:4200")
public class TransactionPaiementController {

    private final TransactionPaiementService transactionPaiementService;
    private final CommandeService commandeService;
    private final FactureService factureService;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    public TransactionPaiementController(TransactionPaiementService transactionPaiementService,
                                         CommandeService commandeService,
                                         FactureService factureService) {
        this.transactionPaiementService = transactionPaiementService;
        this.commandeService = commandeService;
        this.factureService = factureService;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @PostMapping("/commande/multiple")
    public ResponseEntity<?> createTransactionForMultipleCommandes(
            @RequestBody Map<String, Object> requestBody
    ) {
        try {
            System.out.println("Received transaction request for multiple commandes: " + requestBody);

            @SuppressWarnings("unchecked")
            List<Long> commandeIds = (List<Long>) requestBody.get("commandeIds");
            if (commandeIds == null || commandeIds.isEmpty()) {
                throw new IllegalArgumentException("commandeIds is required and cannot be empty");
            }

            Double montant = Double.valueOf(requestBody.get("montant").toString());
            String paymentStatus = (String) requestBody.get("paymentStatus");
            String methodePaiement = (String) requestBody.get("methodePaiement");
            String dateTransactionStr = (String) requestBody.get("dateTransaction");

            TransactionPaiement transaction = new TransactionPaiement();
            transaction.setMontant(montant);
            transaction.setPaymentStatus(paymentStatus);
            transaction.setMethodePaiement(methodePaiement);
            transaction.setDateTransaction(dateTransactionStr != null
                    ? LocalDateTime.parse(dateTransactionStr)
                    : LocalDateTime.now());

            if (transaction.getMontant() == null) {
                throw new IllegalArgumentException("Montant is required");
            }
            if (transaction.getMethodePaiement() == null || transaction.getMethodePaiement().trim().isEmpty()) {
                throw new IllegalArgumentException("MethodePaiement is required");
            }

            double montantInDinars = transaction.getMontant();
            long amountInCents = (long) (montantInDinars * 100);
            System.out.println("Amount in cents for Stripe: " + amountInCents);

            if (amountInCents < 50) {
                throw new IllegalArgumentException("Amount must be at least 50 cents in USD");
            }

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("USD")
                    .setDescription("Transaction for commandes " + commandeIds)
                    .build();

            System.out.println("Creating Stripe PaymentIntent");
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            System.out.println("PaymentIntent created: " + paymentIntent.getId());

            transaction.setPaymentIntentId(paymentIntent.getId());
            transaction.setPaymentStatus("created");

            System.out.println("Calling ajouterTransaction for multiple commandes");
            TransactionPaiement savedTransaction = transactionPaiementService.ajouterTransaction(commandeIds, transaction);

            Map<String, String> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("transactionId", savedTransaction.getId().toString());

            System.out.println("Transaction created successfully, clientSecret: " + paymentIntent.getClientSecret());
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (StripeException e) {
            e.printStackTrace();
            return new ResponseEntity<>("Stripe error: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("An error occurred while processing the payment: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateTransactionStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String status = request.get("status");
            if (status == null || status.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Status is required"));
            }

            System.out.println("Updating transaction " + id + " to status: " + status);
            TransactionPaiement transaction = transactionPaiementService.getTransactionById(id);
            transaction.setPaymentStatus(status);
            TransactionPaiement updatedTransaction = transactionPaiementService.updateTransactionStatus(id, status);

            if ("succeeded".equalsIgnoreCase(status)) {
                Set<Commande> commandes = transaction.getCommandes();
                System.out.println("Transaction succeeded, processing " + commandes.size() + " commandes");

                // Update all commandes to PAID and validate data
                for (Commande commande : commandes) {
                    System.out.println("Updating commande " + commande.getId() + " to PAID");
                    commande.setStatus(Commande.OrderStatus.PAID);

                    // Validate Commande data
                    if (commande.getTelephone() == null || commande.getTelephone().trim().isEmpty()) {
                        System.out.println("Commande " + commande.getId() + " has no telephone, setting default");
                        commande.setTelephone("N/A");
                    }
                    if (commande.getGouvernement() == null || commande.getGouvernement().trim().isEmpty()) {
                        System.out.println("Commande " + commande.getId() + " has no gouvernement, setting default");
                        commande.setGouvernement("N/A");
                    }
                    if (commande.getAdresse() == null || commande.getAdresse().trim().isEmpty()) {
                        System.out.println("Commande " + commande.getId() + " has no adresse, setting default");
                        commande.setAdresse("N/A");
                    }
                    if (commande.getTotal() == null || commande.getTotal() <= 0) {
                        System.out.println("Commande " + commande.getId() + " has invalid total: " + commande.getTotal());
                        throw new IllegalStateException("Commande " + commande.getId() + " has invalid total: " + commande.getTotal());
                    }
                    if (commande.getUser() == null) {
                        System.out.println("Commande " + commande.getId() + " has no user associated");
                        throw new IllegalStateException("Commande " + commande.getId() + " has no user associated");
                    }
                    if (commande.getUser().getNom() == null || commande.getUser().getNom().trim().isEmpty()) {
                        System.out.println("User for commande " + commande.getId() + " has no nom, setting default");
                        commande.getUser().setNom("N/A");
                    }
                    if (commande.getUser().getEmail() == null || commande.getUser().getEmail().trim().isEmpty()) {
                        System.out.println("User for commande " + commande.getId() + " has no email, setting default");
                        commande.getUser().setEmail("N/A");
                    }
                    if (commande.getUser().getNumeroDeTelephone() == null || commande.getUser().getNumeroDeTelephone().trim().isEmpty()) {
                        System.out.println("User for commande " + commande.getId() + " has no numeroDeTelephone, setting default");
                        commande.getUser().setNumeroDeTelephone("N/A");
                    }

                    commandeService.saveCommande(commande);
                }

                // Create a single facture for the transaction
                System.out.println("Creating a single facture for transaction " + id);
                Facture facture = new Facture();
                // Use the first commande for basic details
                Commande firstCommande = commandes.iterator().next();
                facture.setCommande(firstCommande);
                // Sum the totals of all commandes
                double totalMontant = commandes.stream()
                        .mapToDouble(commande -> commande.getTotal() != null ? commande.getTotal() : 0.0)
                        .sum();
                facture.setMontantTotal(totalMontant);
                facture.setDateFacture(LocalDate.now());
                facture.setNumeroFacture("FACT-TRANS-" + id + "-" + System.currentTimeMillis());
                facture.setUser(firstCommande.getUser());

                // Aggregate all lignesCommande from all commandes into one facture
                List<LigneFacture> allLignesFacture = commandes.stream()
                        .flatMap(commande -> {
                            if (commande.getLignesCommande() == null || commande.getLignesCommande().isEmpty()) {
                                System.out.println("Commande " + commande.getId() + " has no lignesCommande");
                                throw new IllegalStateException("Commande " + commande.getId() + " has no lignesCommande");
                            }
                            return commande.getLignesCommande().stream();
                        })
                        .map(ligneCommande -> {
                            if (ligneCommande.getProduit() == null) {
                                throw new IllegalStateException("LigneCommande in commande has no produit");
                            }
                            if (ligneCommande.getTotal() == null || ligneCommande.getTotal() <= 0) {
                                throw new IllegalStateException("LigneCommande has invalid total: " + ligneCommande.getTotal());
                            }
                            LigneFacture ligneFacture = new LigneFacture();
                            ligneFacture.setProduit(ligneCommande.getProduit());
                            ligneFacture.setQte(ligneCommande.getQte() != null ? ligneCommande.getQte() : 0);
                            ligneFacture.setPrixUnitaire(ligneCommande.getPrixUnitaire() != null ? ligneCommande.getPrixUnitaire() : 0.0);
                            ligneFacture.setTotal(ligneCommande.getTotal());
                            ligneFacture.setTtc(ligneCommande.getTtc() != null ? ligneCommande.getTtc() : ligneCommande.getTotal());
                            return ligneFacture;
                        })
                        .collect(Collectors.toList());
                facture.setLignesFacture(allLignesFacture);

                Facture savedFacture = factureService.saveFacture(facture);
                System.out.println("Facture created with ID: " + savedFacture.getId() + " for transaction " + id);
            }

            return ResponseEntity.ok(updatedTransaction);
        } catch (RuntimeException e) {
            System.err.println("RuntimeException in updateTransactionStatus: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Exception in updateTransactionStatus: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating transaction status: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransactionById(@PathVariable Long id) {
        try {
            TransactionPaiement transaction = transactionPaiementService.getTransactionById(id);
            return ResponseEntity.ok(transaction);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving transaction: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllTransactions() {
        try {
            List<TransactionPaiement> transactions = transactionPaiementService.getAllTransactions();
            return transactions.isEmpty()
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving all transactions: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        try {
            transactionPaiementService.supprimerTransaction(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error deleting transaction: " + e.getMessage()));
        }
    }
}