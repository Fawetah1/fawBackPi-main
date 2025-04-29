package com.example.usermanagementbackend.service;

import com.example.usermanagementbackend.entity.Commande;
import com.example.usermanagementbackend.entity.TransactionPaiement;
import com.example.usermanagementbackend.repository.CommandeRepository;
import com.example.usermanagementbackend.repository.TransactionPaiementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.usermanagementbackend.entity.Commande.OrderStatus;

@Service
public class TransactionPaiementService {

    @Autowired
    private TransactionPaiementRepository transactionRepository;

    @Autowired
    private CommandeRepository commandeRepository;

    public TransactionPaiement ajouterTransaction(List<Long> commandeIds, TransactionPaiement transaction) {
        try {
            System.out.println("Fetching Commandes with IDs: " + commandeIds);
            List<Commande> commandes = commandeRepository.findAllById(commandeIds);
            if (commandes.size() != commandeIds.size()) {
                throw new RuntimeException("Une ou plusieurs commandes non trouvées : " + commandeIds);
            }
            System.out.println("Commandes found: " + commandes.stream().map(Commande::getId).collect(Collectors.toList()));

            for (Commande commande : commandes) {
                if (commande.getStatus() != OrderStatus.PENDING && commande.getStatus() != OrderStatus.PENDING_PAYMENT) {
                    throw new IllegalStateException("Commande " + commande.getId() + " has status " + commande.getStatus() + ", expected PENDING or PENDING_PAYMENT");
                }
            }

            double combinedTotal = commandes.stream()
                    .mapToDouble(Commande::getTotal)
                    .sum();
            System.out.println("Validating transaction montant: " + transaction.getMontant() + " against combined total: " + combinedTotal);
            double tolerance = 0.01;
            if (transaction.getMontant() == null || Math.abs(transaction.getMontant() - combinedTotal) > tolerance) {
                throw new IllegalArgumentException("Le montant de la transaction (" + transaction.getMontant() +
                        ") ne correspond pas au total des commandes (" + combinedTotal + ")");
            }

            System.out.println("Validating methodePaiement: " + transaction.getMethodePaiement());
            if (transaction.getMethodePaiement() == null || transaction.getMethodePaiement().trim().isEmpty()) {
                throw new IllegalArgumentException("La méthode de paiement est obligatoire");
            }

            System.out.println("Linking transaction to commandes");
            transaction.setCommandes(Set.copyOf(commandes));

            System.out.println("Saving transaction");
            TransactionPaiement savedTransaction = transactionRepository.save(transaction);

            System.out.println("Ensuring all commandes are in PENDING status");
            for (Commande commande : commandes) {
                if (commande.getStatus() == null || commande.getStatus() == OrderStatus.PENDING) {
                    commande.setStatus(OrderStatus.PENDING);
                } else if (commande.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    System.out.println("Commande " + commande.getId() + " already in PENDING_PAYMENT, no update needed");
                } else {
                    throw new IllegalStateException("Cannot transition Commande " + commande.getId() + " with status " + commande.getStatus() + " to PENDING");
                }
            }
            commandeRepository.saveAll(commandes);

            System.out.println("Transaction saved successfully: " + savedTransaction.getId());
            return savedTransaction;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to add transaction: " + e.getMessage(), e);
        }
    }

    public TransactionPaiement updateTransactionStatus(Long transactionId, String status) {
        TransactionPaiement transaction = transactionRepository.findByIdWithCommandes(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée avec l'ID: " + transactionId));

        transaction.setPaymentStatus(status);
        TransactionPaiement updatedTransaction = transactionRepository.save(transaction);

        if ("succeeded".equalsIgnoreCase(status)) {
            Set<Commande> commandes = transaction.getCommandes();
            for (Commande commande : commandes) {
                commande.setStatus(OrderStatus.PAID);
            }
            commandeRepository.saveAll(commandes);
        }

        return updatedTransaction;
    }

    public TransactionPaiement getTransactionById(Long id) {
        return transactionRepository.findByIdWithCommandes(id)
                .orElseThrow(() -> new RuntimeException("Transaction non trouvée avec l'ID: " + id));
    }

    public List<TransactionPaiement> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public void supprimerTransaction(Long id) {
        TransactionPaiement transaction = getTransactionById(id);
        transactionRepository.delete(transaction);
    }
}