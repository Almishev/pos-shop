package in.bushansirgur.billingsoftware.config;

import in.bushansirgur.billingsoftware.filter.JwtRequestFilter;
import in.bushansirgur.billingsoftware.service.impl.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService appUserDetailsService;
    private final JwtRequestFilter jwtRequestFilter;
    
    @Value("${allowed.origins:http://localhost:3001,http://localhost:5173,http://192.168.80.101:3001,http://192.168.80.120:3001}")
    private String allowedOrigins;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                    // Allow preflight CORS requests without authentication
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/login","/api/v1.0/login","encode").permitAll()
                    .requestMatchers("/health","/api/v1.0/health").permitAll()
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
                            "/loyalty/**",
                            "/api/v1.0/categories",
                            "/api/v1.0/category",
                            "/api/v1.0/items",
                            "/api/v1.0/items/**",
                            "/api/v1.0/dashboard"
                    ).hasAnyRole("USER", "ADMIN")
                    // Orders - require authenticated USER/ADMIN
                    .requestMatchers("/orders", "/orders/**", "/api/v1.0/orders", "/api/v1.0/orders/**").hasAnyRole("USER", "ADMIN")
                    // Read-only fiscal device endpoints for USER and ADMIN
                    .requestMatchers(
                            "/admin/fiscal-devices",
                            "/admin/devices/*/status",
                            "/admin/devices/*/ready",
                            "/api/v1.0/admin/fiscal-devices"
                    ).hasAnyRole("USER", "ADMIN")
                    // Fiscal receipts
                    .requestMatchers("/admin/receipts", "/admin/receipts/**").hasAnyRole("USER", "ADMIN")
                    // Force end session - admin only (MUST be before general cash-drawer rule)
                    .requestMatchers("/api/v1.0/cash-drawer/force-end/**").hasRole("ADMIN")
                    // Cash drawer control endpoints
                    .requestMatchers(
                            "/api/v1.0/cash-drawer/**"
                    ).hasAnyRole("USER", "ADMIN")
                    // Fiscal reports - MUST be before /reports/** rule to avoid conflicts
                    // Allow USER to generate shift reports and view all reports; other fiscal reports stay admin-only
                    .requestMatchers("/admin/fiscal-reports", "/admin/fiscal-reports/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/api/admin/fiscal-reports", "/api/admin/fiscal-reports/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/api/v1.0/admin/fiscal-reports", "/api/v1.0/admin/fiscal-reports/**").hasAnyRole("USER", "ADMIN")
                    // Fiscal reports stats for dashboard
                    .requestMatchers("/admin/fiscal-reports/stats/**").hasAnyRole("USER", "ADMIN")
                    // Label endpoints - simplify: allow without auth to avoid 403 during printing
                    .requestMatchers("/admin/labels/**").permitAll()
                    .requestMatchers("/api/v1.0/admin/labels/**").permitAll()
                    // Promotions endpoints - align with labels (permit for UI calls)
                    .requestMatchers("/admin/promotions/**").permitAll()
                    .requestMatchers("/api/v1.0/admin/promotions/**").permitAll()
                    .requestMatchers("/items/effective").hasAnyRole("USER", "ADMIN")
                    // Admin-only endpoints (users, inventory, categories) - fiscal reports handled above
                    .requestMatchers("/admin/users/**", "/api/admin/users/**", "/api/v1.0/admin/users/**", "/admin/inventory/**", "/api/admin/inventory/**", "/api/v1.0/admin/inventory/**", "/admin/categories/**", "/api/admin/categories/**", "/api/v1.0/admin/categories/**", "/reports/**", "/inventory", "/inventory/**").hasRole("ADMIN")
                    .requestMatchers("/inventory/auto/**").hasAnyRole("USER", "ADMIN")
                    // POS card payments endpoints
                    .requestMatchers("/pos-payments/**", "/api/v1.0/pos-payments/**").hasAnyRole("USER", "ADMIN")
                    .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                    var authc = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    response.setStatus(403);
                    response.setContentType("application/json");
                    try {
                        response.getWriter().write("{\"error\":\"Access Denied\",\"uri\":\"" + request.getRequestURI() + "\",\"method\":\"" + request.getMethod() + "\",\"roles\":\"" + (authc != null ? authc.getAuthorities() : "none") + "\"}");
                    } catch (Exception e) {
                        // Ignore
                    }
                }))
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
        // Parse allowed origins from environment variable
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        
        // Use allowedOriginPatterns instead of setAllowedOrigins to support wildcards
        // This allows all origins from local network (192.168.80.x) for development
        config.addAllowedOriginPattern("http://192.168.80.*:*");
        config.addAllowedOriginPattern("http://localhost:*");
        
        // Also add specific origins from environment variable as patterns
        for (String origin : origins) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                // Convert specific origins to patterns for flexibility
                if (trimmed.contains("192.168.80")) {
                    config.addAllowedOriginPattern("http://192.168.80.*:*");
                } else {
                    config.addAllowedOriginPattern(trimmed);
                }
            }
        }
        
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
