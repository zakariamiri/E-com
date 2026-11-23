package org.sid.Auth_service.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) { }
