package org.sid.Auth_service.controller;

import jakarta.validation.Valid;
import org.sid.Auth_service.dto.*;
import org.sid.Auth_service.service.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final LoginService login;
    private final UserService users;
    public AuthController(LoginService login, UserService users) { this.login = login; this.users = users; }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(login.login(request));
    }
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) { return users.findById(Long.valueOf(authentication.getName())); }
}
