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
        // Paths are relative to server.servlet.context-path (/api/v1.0)
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/login", "/health").permitAll()
                    .requestMatchers("/uploads/**").permitAll()

                    // Cash drawer — cashiers operate the till; admin only force-end / listing
                    .requestMatchers("/cash-drawer/force-end/**").hasRole("ADMIN")
                    .requestMatchers(
                            "/cash-drawer/debug/**",
                            "/cash-drawer/active-sessions",
                            "/cash-drawer/sessions/**"
                    ).hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/cash-drawer/start", "/cash-drawer/end/**").hasRole("USER")
                    .requestMatchers(HttpMethod.GET, "/cash-drawer/active").hasRole("USER")
                    .requestMatchers("/cash-drawer/**").hasRole("USER")

                    // Fiscal devices — writes admin-only; reads for USER+ADMIN
                    .requestMatchers(HttpMethod.POST, "/admin/fiscal-devices", "/admin/fiscal-devices/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/admin/fiscal-devices", "/admin/fiscal-devices/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/admin/fiscal-devices", "/admin/fiscal-devices/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/admin/fiscal-devices", "/admin/fiscal-devices/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/admin/devices/*/status", "/admin/devices/*/ready").hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/devices/*/x-report", "/admin/devices/*/z-report").hasRole("USER")
                    .requestMatchers("/admin/devices/**").hasRole("ADMIN")

                    // Fiscal receipts
                    .requestMatchers("/admin/receipts", "/admin/receipts/**").hasAnyRole("USER", "ADMIN")

                    // Fiscal reports — shift/Z are cashier-only; archive admin-only
                    .requestMatchers(HttpMethod.POST, "/admin/fiscal-reports/archive/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/fiscal-reports/shift", "/admin/fiscal-reports/z-report").hasRole("USER")
                    .requestMatchers("/admin/fiscal-reports", "/admin/fiscal-reports/**").hasAnyRole("USER", "ADMIN")

                    // Labels & promotions — authenticated (not public)
                    .requestMatchers("/admin/labels/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/admin/promotions/**").hasAnyRole("USER", "ADMIN")

                    // Admin-only management
                    .requestMatchers(HttpMethod.POST, "/admin/items", "/admin/items/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/admin/items", "/admin/items/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/admin/items", "/admin/items/**").hasRole("ADMIN")
                    .requestMatchers(
                            "/admin/users/**",
                            "/admin/categories/**",
                            "/admin/inventory/**",
                            "/admin/import/**"
                    ).hasRole("ADMIN")
                    .requestMatchers("/reports/**").hasRole("ADMIN")
                    .requestMatchers("/dashboard", "/dashboard/**").hasRole("ADMIN")
                    .requestMatchers("/inventory/auto/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/inventory", "/inventory/**").hasRole("ADMIN")

                    // Orders — create sales = cashier only; refund/view for both; delete/archive admin
                    .requestMatchers(HttpMethod.DELETE, "/orders/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/orders/archive/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/orders/*/refund").hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/orders").hasRole("USER")
                    .requestMatchers("/orders", "/orders/**").hasAnyRole("USER", "ADMIN")

                    // Catalog & loyalty
                    .requestMatchers(
                            "/categories",
                            "/category",
                            "/items",
                            "/items/**",
                            "/loyalty/**"
                    ).hasAnyRole("USER", "ADMIN")

                    // POS card payments
                    .requestMatchers("/pos-payments/**").hasAnyRole("USER", "ADMIN")

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
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        
        config.addAllowedOriginPattern("http://192.168.80.*:*");
        config.addAllowedOriginPattern("http://localhost:*");
        
        for (String origin : origins) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.contains("192.168.80")) {
                    config.addAllowedOriginPattern("http://192.168.80.*:*");
                } else {
                    config.addAllowedOriginPattern(trimmed);
                }
            }
        }
        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
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
