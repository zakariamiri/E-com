package org.sid.Notification_service.dto;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled
) {
}
