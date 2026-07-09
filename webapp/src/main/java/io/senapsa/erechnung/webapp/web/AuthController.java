package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AppUser;
import io.senapsa.erechnung.webapp.repo.AppUserRepository;
import io.senapsa.erechnung.webapp.security.JwtService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AppUser user = users.findByEmail(request.email()).orElse(null);
        if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiExceptions.BadRequest("E-Mail oder Passwort ist nicht korrekt.");
        }
        String token = jwt.issue(user);
        return ResponseEntity.ok(new LoginResponse(token, user.getEmail(),
                user.getRole().name(), user.isNavExpanded()));
    }

    public record LoginRequest(@Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, String email, String role, boolean navExpanded) {
    }
}
