package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.AuthRequest;
import in.bushansirgur.billingsoftware.io.AuthResponse;
import in.bushansirgur.billingsoftware.service.LicenseService;
import in.bushansirgur.billingsoftware.service.UserService;
import in.bushansirgur.billingsoftware.service.impl.AppUserDetailsService;
import in.bushansirgur.billingsoftware.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService appUserDetailsService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LicenseService licenseService;

    @PostMapping({"/login", "/api/v1.0/login"})
    public AuthResponse login(@RequestBody AuthRequest request) throws Exception {
        licenseService.assertLicenseActive();
        String email = resolveEmail(request);
        authenticate(email, request.getPassword());
        final UserDetails userDetails = appUserDetailsService.loadUserByUsername(email);
        final String jwtToken = jwtUtil.generateToken(userDetails);
        String role = userService.getUserRole(email);
        String name = userService.readUsers()
                .stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst()
                .map(u -> u.getName())
                .orElse(email);
        return new AuthResponse(email, name, jwtToken, role);
    }

    private String resolveEmail(AuthRequest request) {
        String providedEmail = request.getEmail() == null ? "" : request.getEmail().trim();
        if (!providedEmail.isEmpty()) {
            return providedEmail;
        }
        try {
            return userService.findEmailByPin(request.getPassword());
        } catch (UsernameNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or password is incorrect");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN is not unique");
        }
    }

    private void authenticate(String email, String password) throws Exception {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (DisabledException e) {
            throw new Exception("User disabled");
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or password is incorrect");
        }
    }
}
