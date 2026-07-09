package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AppUser;
import io.senapsa.erechnung.webapp.repo.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Angemeldeter Benutzer: Profil und je Benutzer gespeicherter Navigationszustand. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AppUserRepository users;

    public MeController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    public Me me(Authentication auth) {
        AppUser user = current(auth);
        return new Me(user.getEmail(), user.getRole().name(), user.isNavExpanded());
    }

    /** Speichert den Zustand der Navigationsleiste je Benutzer. */
    @PatchMapping("/nav")
    @Transactional
    public Me setNav(@RequestBody NavRequest req, Authentication auth) {
        AppUser user = current(auth);
        user.setNavExpanded(req.expanded());
        users.save(user);
        return new Me(user.getEmail(), user.getRole().name(), user.isNavExpanded());
    }

    private AppUser current(Authentication auth) {
        return users.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiExceptions.NotFound("Benutzer wurde nicht gefunden."));
    }

    public record Me(String email, String role, boolean navExpanded) {
    }

    public record NavRequest(boolean expanded) {
    }
}
