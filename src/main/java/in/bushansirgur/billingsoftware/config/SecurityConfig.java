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
        System.out.println("Cash drawer endpoints configured:");
        System.out.println("  - /api/v1.0/cash-drawer/force-end/** -> ADMIN only");
        System.out.println("  - /api/v1.0/cash-drawer/** -> USER, ADMIN");
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
                    // Allow USER to generate shift reports and view all reports; other fiscal reports stay admin-only
                    .requestMatchers("/admin/fiscal-reports", "/admin/fiscal-reports/**").hasAnyRole("USER", "ADMIN")
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
                    // Admin-only endpoints (users, inventory) - fiscal reports handled above
                    .requestMatchers("/admin/users/**", "/admin/inventory/**", "/reports/**", "/inventory", "/inventory/**").hasRole("ADMIN")
                    .requestMatchers("/inventory/auto/**").hasAnyRole("USER", "ADMIN")
                    .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                    System.out.println("=== AccessDenied === URI: " + request.getRequestURI());
                    var authc = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    System.out.println("Auth present: " + (authc != null) + ", user=" + (authc != null ? authc.getName() : "-") + ", roles=" + (authc != null ? authc.getAuthorities() : "-"));
                    response.setStatus(403);
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
        config.setAllowedOrigins(List.of("http://localhost:3001", "http://localhost:5173", "http://192.168.80.101:3001"));
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
