package com.example.usermanagementbackend.service;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class StripeService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isEmpty()) {
            throw new RuntimeException("Stripe secret key is not set. Please check your application.properties.");
        }

        // Log the key length to ensure it's being injected correctly
        System.out.println("Stripe Secret Key Length: " + stripeSecretKey.length());

        // Set the Stripe API key
        Stripe.apiKey = stripeSecretKey;
        System.out.println("Stripe API Key initialized successfully");
    }

    public String createPaymentIntent(Long amount, String currency) throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(
                PaymentIntentCreateParams.builder()
                        .setAmount(amount) // Amount in cents
                        .setCurrency(currency)
                        .addPaymentMethodType("card")
                        .build()
        );
        return paymentIntent.getClientSecret();
    }
}
