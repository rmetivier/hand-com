package fr.handcom.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentification HTTP Basic sans état, avec un compte admin défini dans la configuration.
 * Le point d'entrée renvoie un 401 sans en-tête WWW-Authenticate pour éviter la popup du navigateur.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Médias : doivent être publics pour qu'Instagram puisse les télécharger
                        .requestMatchers("/media/**").permitAll()
                        // Retours OAuth : le navigateur y arrive sans en-tête d'authentification (protégés par "state")
                        .requestMatchers("/api/oauth/*/callback").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(AppProperties props, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(props.adminUsername())
                .password(encoder.encode(props.adminPassword()))
                .roles("ADMIN")
                .build());
    }
}
