package org.sid.Order_service.services;

import org.sid.Order_service.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/api/users")
public interface AuthClient {
    @GetMapping("/{id}/summary")
    UserSummaryResponse getUserSummary(@PathVariable("id") Long userId);
}
