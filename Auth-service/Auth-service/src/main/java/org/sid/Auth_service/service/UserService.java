package org.sid.Auth_service.service;

import org.sid.Auth_service.dto.CreateUserRequest;
import org.sid.Auth_service.dto.UpdateUserRequest;
import org.sid.Auth_service.dto.UserResponse;
import org.sid.Auth_service.entity.Role;
import org.sid.Auth_service.entity.User;
import org.sid.Auth_service.repository.RoleRepository;
import org.sid.Auth_service.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {
    private static final String DEFAULT_ROLE = "USER";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final RoleRepository roles;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        checkEmailAvailability(email, null);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir au plus 72 octets en UTF-8.");
        }

        User user = new User();
        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(defaultRole());
        return toResponse(saveUser(user));
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse findById(Long id) {
        return toResponse(requireUser(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = requireUser(id);
        String email = normalizeEmail(request.email());
        checkEmailAvailability(email, id);

        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        user.setEmail(email);
        return toResponse(saveUser(user));
    }

    @Transactional
    public void delete(Long id) {
        users.delete(requireUser(id));
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Utilisateur introuvable."));
    }

    private void checkEmailAvailability(String email, Long currentUserId) {
        if (users.findByEmailIgnoreCase(email)
                .filter(user -> !user.getId().equals(currentUserId)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé.");
        }
    }

    private User saveUser(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException cause
                    && "23505".equals(cause.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé.", exception);
            }
            throw exception;
        }
    }

    private Role defaultRole() {
        return roles.findByName(DEFAULT_ROLE).orElseGet(() -> {
            roles.insertIfAbsent(DEFAULT_ROLE);
            return roles.findByName(DEFAULT_ROLE).orElseThrow();
        });
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.isEnabled(), user.getCreatedAt(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet()));
    }
}
