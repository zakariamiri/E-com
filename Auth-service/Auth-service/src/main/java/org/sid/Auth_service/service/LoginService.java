package org.sid.Auth_service.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.sid.Auth_service.dto.*;
import org.sid.Auth_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class LoginService {
    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttl;
    private final String dummyHash;

    public LoginService(UserRepository users, UserService userService, PasswordEncoder passwords, JwtEncoder encoder,
                        @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.ttl-seconds}") long ttl) {
        if (ttl < 60 || ttl > 3600) throw new IllegalArgumentException("JWT TTL must be between 60 and 3600 seconds");
        this.users = users; this.userService = userService; this.passwords = passwords;
        this.encoder = encoder; this.issuer = issuer; this.ttl = ttl;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect.");
        var user = users.findByEmailIgnoreCase(request.email().strip().toLowerCase(Locale.ROOT));
        boolean valid = passwords.matches(request.password(), user.map(u -> u.getPasswordHash()).orElse(dummyHash));
        if (!valid || user.isEmpty() || !user.get().isEnabled())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect.");
        UserResponse response = userService.findById(user.get().getId());
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(String.valueOf(response.id()))
                .audience(List.of("novastore-auth-api")).issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString()).claim("roles", response.roles()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", ttl, response);
    }
}
