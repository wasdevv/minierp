package dev.wasdevv.minierp.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brute-force guard for the login form. Failures are counted per client address and username together, so
 * someone guessing passwords is stopped without letting anyone lock the operator out from elsewhere. In
 * memory on purpose: the app runs as a single instance, and a restart clearing the counters is acceptable.
 */
@Component
public class LoginAttempts {

    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public LoginAttempts(@Value("${minierp.login.max-attempts:5}") int maxAttempts,
                         @Value("${minierp.login.lock-duration:15m}") Duration lockDuration, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public boolean isLocked(String address, String username) {
        Entry entry = entries.get(key(address, username));
        return entry != null && entry.lockedUntil != null && clock.instant().isBefore(entry.lockedUntil);
    }

    public void failed(String address, String username) {
        Instant now = clock.instant();
        entries.compute(key(address, username), (key, old) -> {
            Entry entry = old == null || (old.lockedUntil != null && !now.isBefore(old.lockedUntil)) ? new Entry() : old;
            entry.failures++;
            if (entry.failures >= maxAttempts) {
                entry.lockedUntil = now.plus(lockDuration);
            }
            return entry;
        });
        if (entries.size() > 10_000) {
            entries.entrySet().removeIf(e -> e.getValue().lockedUntil == null || !now.isBefore(e.getValue().lockedUntil));
        }
    }

    public void succeeded(String address, String username) {
        entries.remove(key(address, username));
    }

    public long lockMinutes() {
        return Math.max(1, lockDuration.toMinutes());
    }

    private static String key(String address, String username) {
        return address + "|" + (username == null ? "" : username.strip().toLowerCase(Locale.ROOT));
    }

    private static final class Entry {
        private int failures;
        private Instant lockedUntil;
    }
}
