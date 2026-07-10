package io.senapsa.erechnung.webapp.config;

import io.senapsa.erechnung.webapp.security.JwtAuthFilter;
import io.senapsa.erechnung.webapp.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Oeffentlich: Login, Branding, Health, API-Doku.
                        .requestMatchers("/api/v1/auth/**", "/api/v1/branding").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Geschuetzte API: Stammdaten/Administration nur ADMIN, restliche API angemeldet.
                        .requestMatchers("/api/v1/stammdaten/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .requestMatchers("/actuator/**").authenticated()
                        // Statische SPA (HTML/JS/CSS) und Client-Routen sind oeffentlich; die
                        // Daten dahinter sind ueber die API geschuetzt. So funktionieren
                        // Deep-Links / Neuladen (F5) auf Unterseiten.
                        .anyRequest().permitAll())
                // Fehlende/ungueltige Authentifizierung -> 401 (Frontend beendet die
                // Sitzung); fehlende Berechtigung bei gueltigem Login -> 403.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
