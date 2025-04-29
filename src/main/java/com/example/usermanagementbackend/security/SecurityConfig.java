package com.example.usermanagementbackend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors().and() // Enable CORS if required
                .csrf().disable()  // Disable CSRF (you can enable it based on your app's needs)
                .httpBasic().disable()  // Disable HTTP Basic Authentication
                .formLogin().disable()  // Disable form-based login
                .authorizeRequests()
                // Allow access to registration and authentication routes
                .requestMatchers("/api/users/register", "/api/auth/**").permitAll()
                // Allow access to Livraison endpoints
                .requestMatchers("/api/livraisons/**").permitAll()
                // Allow access to Livreur endpoints
                .requestMatchers("/api/livreurs/**").permitAll()
                .requestMatchers("/ws/**", "/topic/**", "/app/**").permitAll()

                .requestMatchers("/api/users/**").permitAll()

                .requestMatchers("/uploads/**").permitAll() // Allow public access to /uploads/
                .requestMatchers("/api/users/photo/**").authenticated() // Require auth for photos
                .requestMatchers("/api/users/verify").permitAll()
                .requestMatchers("/notifications/**").permitAll()
                .requestMatchers("/api/produits/**").permitAll()
                .requestMatchers("/promotions/**").permitAll()
                .requestMatchers("/api/fidelite/**").permitAll()
                .requestMatchers("/api/purchases/**").permitAll()// Added for Fidelite endpoints
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/stock/**").permitAll()
                .requestMatchers("/api/recommendations/**").permitAll()
                // All other requests must be authenticated

                .requestMatchers("/api/commandes/**").permitAll()
                .requestMatchers("/api/factures/**").permitAll()
                .requestMatchers("/api/transactions/**").permitAll()
                .requestMatchers("/api/transactions/commande/*").permitAll()
                .requestMatchers("/api/lignes-commande/*").permitAll()
                .requestMatchers("/api/lignes-facture/*").permitAll()


                .anyRequest().authenticated();

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername("admin")
                .password(passwordEncoder.encode("adminpass"))
                .roles("ADMIN")
                .build();

        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("userpass"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}

