package in.bushansirgur.billingsoftware.filter;

import in.bushansirgur.billingsoftware.service.impl.AppUserDetailsService;
import in.bushansirgur.billingsoftware.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final AppUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();
        String requestMethod = request.getMethod();

        System.out.println("=== JwtRequestFilter.doFilterInternal ===");
        System.out.println("Request URI: " + requestURI);
        System.out.println("Request Method: " + requestMethod);
        System.out.println("Authorization Header: " + (authorizationHeader != null ? authorizationHeader.substring(0, Math.min(20, authorizationHeader.length())) + "..." : "null"));

        String email = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            email = jwtUtil.extractUsername(jwt);
            System.out.println("Extracted email: " + email);
        } else {
            System.out.println("No valid Authorization header found");
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                System.out.println("UserDetails loaded: " + userDetails.getUsername());
                System.out.println("User authorities: " + userDetails.getAuthorities());
                
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    System.out.println("JWT token is valid");
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    System.out.println("Authentication set in SecurityContext");
                } else {
                    System.out.println("JWT token is invalid");
                }
            } catch (Exception e) {
                System.err.println("Error in JWT processing: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (email == null) {
            System.out.println("No email extracted from JWT");
        } else {
            System.out.println("Authentication already exists in SecurityContext");
        }
        
        System.out.println("Proceeding to next filter/controller");
        filterChain.doFilter(request, response);
    }
}
