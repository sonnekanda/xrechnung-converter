package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AppUser;
import io.senapsa.erechnung.webapp.domain.Role;
import io.senapsa.erechnung.webapp.repo.AppUserRepository;
import io.senapsa.erechnung.webapp.service.AuditService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Benutzerverwaltung (nur Rolle ADMIN, Pfad /admin/**). */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserController(AppUserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAll().stream()
                .map(u -> new UserView(u.getId().toString(), u.getEmail(), u.getRole().name()))
                .toList();
    }

    @PostMapping
    public UserView create(@RequestBody CreateUserRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ApiExceptions.Conflict("Ein Benutzer mit dieser E-Mail ist bereits vorhanden.");
        }
        Role role = "ADMIN".equalsIgnoreCase(req.role()) ? Role.ADMIN : Role.BENUTZER;
        AppUser user = users.save(new AppUser(req.email(), encoder.encode(req.password()), role));
        audit.log(null, "BENUTZER_ANGELEGT", user.getEmail() + " (" + role + ")");
        return new UserView(user.getId().toString(), user.getEmail(), user.getRole().name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Benutzer wurde nicht gefunden."));
        if (user.getEmail().equals(auth.getName())) {
            throw new ApiExceptions.Conflict("Sie können Ihr eigenes Konto nicht löschen.");
        }
        if (user.getRole() == Role.ADMIN && countAdmins() <= 1) {
            throw new ApiExceptions.Conflict("Der letzte Administrator kann nicht gelöscht werden.");
        }
        users.delete(user);
        audit.log(null, "BENUTZER_GELOESCHT", user.getEmail());
        return ResponseEntity.noContent().build();
    }

    private long countAdmins() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();
    }

    public record CreateUserRequest(@Email String email, @NotBlank String password, String role) {
    }

    public record UserView(String id, String email, String role) {
    }
}
