package io.senapsa.erechnung.webapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account-Lockout gegen Brute-Force (Sicherheitshärtung): nach zu vielen
 * fehlgeschlagenen Anmeldungen wird das Konto für eine definierte Dauer gesperrt.
 * In-Memory je E-Mail; für Single-Tenant/kleine Instanzen ausreichend.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final Duration lockDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${erechnung.auth.max-login-failures:5}") int maxFailures,
                               @Value("${erechnung.auth.lock-minutes:15}") long lockMinutes) {
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    public boolean isLocked(String email) {
        Attempt a = attempts.get(key(email));
        return a != null && a.lockedUntil != null && Instant.now().isBefore(a.lockedUntil);
    }

    /** Registriert einen Fehlversuch; liefert true, wenn dadurch gesperrt wird. */
    public boolean recordFailure(String email) {
        Attempt a = attempts.computeIfAbsent(key(email), k -> new Attempt());
        synchronized (a) {
            a.failures++;
            if (a.failures >= maxFailures) {
                a.lockedUntil = Instant.now().plus(lockDuration);
                return true;
            }
            return false;
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    public long lockMinutes() {
        return lockDuration.toMinutes();
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static final class Attempt {
        int failures;
        Instant lockedUntil;
    }
}
