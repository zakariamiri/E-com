package org.sid.Order_service.services;

import org.sid.Order_service.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/api/users", configuration = AuthClient.InternalAccess.class)
public interface AuthClient {
    @GetMapping("/{id}/summary")
    UserSummaryResponse getUserSummary(@PathVariable("id") Long userId);

    class InternalAccess {
        @org.springframework.context.annotation.Bean
        public feign.RequestInterceptor authInternalToken(
                @org.springframework.beans.factory.annotation.Value("${AUTH_INTERNAL_TOKEN:}") String token) {
            return request -> { if (!token.isBlank()) request.header("X-Internal-Token", token); };
        }
    }
}
