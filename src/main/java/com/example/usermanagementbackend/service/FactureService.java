package com.example.usermanagementbackend.service;

import com.example.usermanagementbackend.entity.*;
import com.example.usermanagementbackend.repository.CommandeRepository;
import com.example.usermanagementbackend.repository.FactureRepository;
import com.example.usermanagementbackend.repository.TransactionPaiementRepository;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class FactureService {

    private static final Logger logger = LoggerFactory.getLogger(FactureService.class);
    private static final double TAX_RATE = 0.20; // Consistent with LigneFactureService

    private final FactureRepository factureRepository;
    private final CommandeRepository commandeRepository;
    private final LigneFactureService ligneFactureService;
    private final TransactionPaiementRepository transactionPaiementRepository;

    public FactureService(FactureRepository factureRepository, CommandeRepository commandeRepository,
                          LigneFactureService ligneFactureService, TransactionPaiementRepository transactionPaiementRepository) {
        this.factureRepository = factureRepository;
        this.commandeRepository = commandeRepository;
        this.ligneFactureService = ligneFactureService;
        this.transactionPaiementRepository = transactionPaiementRepository;
    }

    public List<Facture> getAllFactures() {
        return factureRepository.findAll();
    }

    public Optional<Facture> getFactureById(Long id) {
        return factureRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Facture> getFactureByTransactionId(Long transactionId) {
        return factureRepository.findByTransactionId(transactionId);
    }

    @Transactional
    public Facture saveFacture(Facture facture) {
        if (facture.getCommande() == null) {
            throw new IllegalArgumentException("Commande doit être spécifiée");
        }
        Commande commande = commandeRepository.findById(facture.getCommande().getId())
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'ID: " + facture.getCommande().getId()));

        // Ensure the commande is fully loaded with its user
        if (commande.getUser() == null) {
            throw new IllegalArgumentException("L'utilisateur de la commande doit être spécifié");
        }

        facture.setCommande(commande);
        User user = commande.getUser();
        facture.setUser(user); // Explicitly set the user to ensure it's linked

        if (user.getCreditLimit() != null && facture.getMontantTotal() != null) {
            if (facture.getMontantTotal() > user.getCreditLimit()) {
                throw new IllegalArgumentException("Le montant total de la facture dépasse la limite de crédit de l'utilisateur");
            }
        }

        if (facture.getLignesFacture() == null || facture.getLignesFacture().isEmpty()) {
            throw new IllegalArgumentException("La facture doit contenir au moins une ligne de facture");
        }

        // Calculate total from LigneFacture
        double calculatedTotal = 0.0;
        for (LigneFacture ligne : facture.getLignesFacture()) {
            if (ligne.getTtc() == null) {
                throw new IllegalArgumentException("Ligne de facture incomplète: TTC manquant");
            }
            calculatedTotal += ligne.getTtc();
        }
        if (facture.getMontantTotal() == null || Math.abs(facture.getMontantTotal() - calculatedTotal) > 0.01) {
            facture.setMontantTotal(calculatedTotal);
            logger.warn("Montant total ajusté à {} pour correspondre aux lignes de facture", calculatedTotal);
        }

        if (facture.getMontantTotal() <= 0) {
            throw new IllegalArgumentException("Le montant total de la facture doit être supérieur à 0");
        }

        if (facture.getDateFacture() == null) {
            facture.setDateFacture(LocalDate.now());
        }
        if (facture.getNumeroFacture() == null) {
            facture.setNumeroFacture("FACT-" + System.currentTimeMillis());
        }

        Facture savedFacture = factureRepository.save(facture);

        // Save LigneFacture entries
        for (LigneFacture ligne : facture.getLignesFacture()) {
            ligne.setFacture(savedFacture);
            ligneFactureService.saveLigneFacture(ligne);
        }

        // Log the facture data before generating PDF
        logger.info("Facture saved: ID={}, User={}, Commande ID={}",
                savedFacture.getId(),
                savedFacture.getUser() != null ? savedFacture.getUser().getNom() : "null",
                savedFacture.getCommande().getId());

        try {
            generateInvoicePDF(savedFacture);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du PDF pour la facture ID {}: {}", savedFacture.getId(), e.getMessage());
            throw new RuntimeException("Échec de la génération du PDF pour la facture", e);
        }

        return savedFacture;
    }

    @Transactional
    public Facture updateFacture(Long id, Facture updatedFacture) {
        Facture existing = factureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture non trouvée avec l'ID: " + id));

        if (updatedFacture.getMontantTotal() != null && updatedFacture.getMontantTotal() <= 0) {
            throw new IllegalArgumentException("Le montant total de la facture doit être supérieur à 0");
        }

        existing.setMontantTotal(updatedFacture.getMontantTotal());
        existing.setDateFacture(updatedFacture.getDateFacture() != null ? updatedFacture.getDateFacture() : LocalDate.now());
        existing.setNumeroFacture(updatedFacture.getNumeroFacture() != null ? updatedFacture.getNumeroFacture() : existing.getNumeroFacture());

        // Ensure the user is set properly during update
        if (updatedFacture.getUser() != null) {
            existing.setUser(updatedFacture.getUser());
        } else if (existing.getUser() == null && existing.getCommande() != null) {
            existing.setUser(existing.getCommande().getUser());
        }

        Facture savedFacture = factureRepository.save(existing);

        // Log the facture data before generating PDF
        logger.info("Facture updated: ID={}, User={}, Commande ID={}",
                savedFacture.getId(),
                savedFacture.getUser() != null ? savedFacture.getUser().getNom() : "null",
                savedFacture.getCommande().getId());

        try {
            generateInvoicePDF(savedFacture);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du PDF pour la facture mise à jour ID {}: {}", savedFacture.getId(), e.getMessage());
            throw new RuntimeException("Échec de la génération du PDF pour la facture mise à jour", e);
        }

        return savedFacture;
    }

    public void deleteFacture(Long id) {
        if (!factureRepository.existsById(id)) {
            throw new RuntimeException("Facture non trouvée avec l'ID: " + id);
        }
        factureRepository.deleteById(id);
    }

    private void generateInvoicePDF(Facture facture) {
        String pdfPath = "invoices/invoice_" + facture.getId() + ".pdf";
        try {
            if (facture == null || facture.getCommande() == null) {
                throw new IllegalArgumentException("Facture ou commande invalide");
            }

            // Validate critical data
            if (facture.getUser() == null) {
                logger.warn("Utilisateur manquant pour la facture ID {}", facture.getId());
                throw new IllegalStateException("Utilisateur manquant pour la facture ID " + facture.getId());
            }

            Path invoicesDir = Paths.get("invoices");
            if (!Files.exists(invoicesDir)) {
                Files.createDirectories(invoicesDir);
                logger.info("Created 'invoices' directory at: {}", invoicesDir.toAbsolutePath());
            }

            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfPath));
            Document doc = new Document(pdfDoc);

            // User Information
            doc.add(new Paragraph("Détails de la Facture").setBold());
            doc.add(new Paragraph(""));
            doc.add(new Paragraph("Informations Utilisateur").setBold());
            String userName = facture.getUser().getNom() != null ? facture.getUser().getNom() : "N/A";
            doc.add(new Paragraph("Nom: " + userName));
            String userEmail = facture.getUser().getEmail() != null ? facture.getUser().getEmail() : "N/A";
            doc.add(new Paragraph("Email: " + userEmail));
            String userPhone = facture.getUser().getNumeroDeTelephone() != null ? facture.getUser().getNumeroDeTelephone() : "N/A";
            doc.add(new Paragraph("Téléphone: " + userPhone));
            doc.add(new Paragraph(""));

            // Delivery Information
            Commande commande = facture.getCommande();
            doc.add(new Paragraph("Informations de Livraison").setBold());
            doc.add(new Paragraph("Téléphone: " + (commande.getTelephone() != null ? commande.getTelephone() : "N/A")));
            doc.add(new Paragraph("Gouvernorat: " + (commande.getGouvernement() != null ? commande.getGouvernement() : "N/A")));
            doc.add(new Paragraph("Adresse: " + (commande.getAdresse() != null ? commande.getAdresse() : "N/A")));
            doc.add(new Paragraph(""));

            // Invoice Details
            doc.add(new Paragraph("Détails de la Facture").setBold());
            doc.add(new Paragraph("Numéro de Facture: " + (facture.getNumeroFacture() != null ? facture.getNumeroFacture() : "N/A")));
            doc.add(new Paragraph("Date de Facture: " + (facture.getDateFacture() != null ? facture.getDateFacture().toString() : "N/A")));
            doc.add(new Paragraph("Commande ID: " + commande.getId()));
            doc.add(new Paragraph("Statut: " + (commande.getStatus() != null ? commande.getStatus().toString() : "N/A")));
            doc.add(new Paragraph(""));

            // Payment Status
            Set<TransactionPaiement> transactions = commande.getTransactions();
            String paymentStatus = transactions.isEmpty() ? "Non payé" : transactions.iterator().next().getPaymentStatus();
            doc.add(new Paragraph("Statut du paiement: " + paymentStatus));
            if (!transactions.isEmpty()) {
                TransactionPaiement transaction = transactions.iterator().next();
                if (transaction.getPaymentIntentId() != null) {
                    doc.add(new Paragraph("ID Paiement Stripe: " + transaction.getPaymentIntentId()));
                }
            }
            doc.add(new Paragraph(""));

            // Invoice Lines Table
            List<LigneFacture> lignesFacture = ligneFactureService.getLignesFactureByFactureId(facture.getId());
            if (!lignesFacture.isEmpty()) {
                Table table = new Table(new float[]{2, 2, 1, 1, 1, 1});
                table.addHeaderCell(new Cell().add(new Paragraph("Produit").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("Quantité").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("Prix Unitaire").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("Total HT").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("TTC").setBold()));

                for (LigneFacture ligne : lignesFacture) {
                    table.addCell(new Cell().add(new Paragraph(ligne.getProduit() != null && ligne.getProduit().getNom() != null ? ligne.getProduit().getNom() : "N/A")));
                    table.addCell(new Cell().add(new Paragraph(ligne.getProduit() != null && ligne.getProduit().getDescription() != null ? ligne.getProduit().getDescription() : "N/A")));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(ligne.getQte()))));
                    table.addCell(new Cell().add(new Paragraph(String.format("%.2f", ligne.getPrixUnitaire()))));
                    table.addCell(new Cell().add(new Paragraph(String.format("%.2f", ligne.getTotal() != null ? ligne.getTotal() : 0.0))));
                    table.addCell(new Cell().add(new Paragraph(String.format("%.2f", ligne.getTtc() != null ? ligne.getTtc() : 0.0))));
                }

                doc.add(table);
                doc.add(new Paragraph(""));
            } else {
                doc.add(new Paragraph("Aucune ligne de facture disponible."));
                doc.add(new Paragraph(""));
            }

            // Total Amount
            doc.add(new Paragraph("Montant Total: TND " + String.format("%.2f", facture.getMontantTotal() != null ? facture.getMontantTotal() : 0.0)).setBold());
            doc.close();
            logger.info("PDF generated successfully at: {}", pdfPath);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du PDF: {} ({})", pdfPath, e.getMessage());
            throw new RuntimeException("Erreur lors de la génération du PDF: " + pdfPath, e);
        }
    }
}