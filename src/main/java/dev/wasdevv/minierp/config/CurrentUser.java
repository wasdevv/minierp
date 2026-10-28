package dev.wasdevv.minierp.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Who is doing the current request, for the "who" columns of the history. */
@Component
public class CurrentUser {

    static final String SYSTEM = "sistema";

    public String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || auth.getName() == null ? SYSTEM : auth.getName();
    }
}
