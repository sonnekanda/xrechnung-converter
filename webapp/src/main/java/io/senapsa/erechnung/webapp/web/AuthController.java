package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AppUser;
import io.senapsa.erechnung.webapp.repo.AppUserRepository;
import io.senapsa.erechnung.webapp.security.JwtService;
import io.senapsa.erechnung.webapp.service.AuditService;
import io.senapsa.erechnung.webapp.service.LoginAttemptService;
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
    private final LoginAttemptService attempts;
    private final AuditService audit;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt,
                         LoginAttemptService attempts, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.audit = audit;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String email = request.email();
        if (attempts.isLocked(email)) {
            audit.logSecurity("LOGIN_GESPERRT", "Konto vorübergehend gesperrt", email);
            throw new ApiExceptions.Conflict("Zu viele Fehlversuche. Bitte in "
                    + attempts.lockMinutes() + " Minuten erneut versuchen.");
        }
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            boolean nowLocked = attempts.recordFailure(email);
            audit.logSecurity("LOGIN_FEHLGESCHLAGEN",
                    nowLocked ? "Konto nach Fehlversuchen gesperrt" : "falsche Zugangsdaten", email);
            throw new ApiExceptions.BadRequest("E-Mail oder Passwort ist nicht korrekt.");
        }
        attempts.recordSuccess(email);
        audit.logSecurity("LOGIN_OK", "Anmeldung erfolgreich", email);
        String token = jwt.issue(user);
        return ResponseEntity.ok(new LoginResponse(token, user.getEmail(),
                user.getRole().name(), user.isNavExpanded()));
    }

    public record LoginRequest(@Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, String email, String role, boolean navExpanded) {
    }
}
