package in.bushansirgur.billingsoftware.config;

import in.bushansirgur.billingsoftware.filter.JwtRequestFilter;
import in.bushansirgur.billingsoftware.service.impl.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService appUserDetailsService;
    private final JwtRequestFilter jwtRequestFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        System.out.println("=== SecurityConfig.securityFilterChain called ===");
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                    // Allow preflight CORS requests without authentication
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/login","encode").permitAll()
                    .requestMatchers("/uploads/**", "/api/v1.0/uploads/**").permitAll()
                    // Allow authenticated users (USER, ADMIN) to access common app resources
                    .requestMatchers(
                            "/categories",
                            "/category",
                            "/items",
                            "/items/**",
                            "/items/generate-barcode",
                            "/items/barcode/**",
                            "/items/search",
                            "/loyalty/**"
                    ).hasAnyRole("USER", "ADMIN")
                    // Read-only fiscal device endpoints for USER and ADMIN
                    .requestMatchers(
                            "/admin/fiscal-devices",
                            "/admin/devices/*/status",
                            "/admin/devices/*/ready"
                    ).hasAnyRole("USER", "ADMIN")
                    // Allow USER to generate shift reports; other fiscal reports stay admin-only
                    .requestMatchers("/admin/fiscal-reports/shift").hasAnyRole("USER", "ADMIN")
                    // Label endpoints - simplify: allow without auth to avoid 403 during printing
                    .requestMatchers("/admin/labels/**").permitAll()
                    .requestMatchers("/api/v1.0/admin/labels/**").permitAll()
                    // Promotions endpoints - align with labels (permit for UI calls)
                    .requestMatchers("/admin/promotions/**").permitAll()
                    .requestMatchers("/api/v1.0/admin/promotions/**").permitAll()
                    .requestMatchers("/items/effective").hasAnyRole("USER", "ADMIN")
                    // Admin-only endpoints (users, other fiscal reports, inventory)
                    .requestMatchers("/admin/**", "/reports/**", "/inventory", "/inventory/**").hasRole("ADMIN")
                    .requestMatchers("/inventory/auto/**").hasAnyRole("USER", "ADMIN")
                    .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }

    private UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*")); // allow all request headers (Accept, Authorization, Content-Type, etc.)
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(appUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(authProvider);
    }


}
