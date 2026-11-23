package org.sid.Auth_service.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public final class AccountAccess {
    private AccountAccess() { }
    public static void requireOwnerOrAdmin(Authentication authentication, Long id) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && !String.valueOf(id).equals(authentication.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès interdit.");
    }
}
