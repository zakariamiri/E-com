package org.sid.Notification_service.service;

import org.sid.Notification_service.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/api/users")
public interface AuthClient {
    @GetMapping("/{id}")
    UserResponse getUserById(@PathVariable("id") Long userId);
}
