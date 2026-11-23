package org.sid.Cart_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "Auth-service", url = "${auth-service.url:http://localhost:8081}", configuration = AuthClient.InternalAccess.class)
public interface AuthClient {
    @GetMapping("/api/users/{id}/summary")
    UserSummary findSummary(@PathVariable("id") Long id);

    record UserSummary(Long id, String email, boolean enabled) {
    }

    class InternalAccess {
        @org.springframework.context.annotation.Bean
        public feign.RequestInterceptor authInternalToken(
                @org.springframework.beans.factory.annotation.Value("${AUTH_INTERNAL_TOKEN:}") String token) {
            return request -> { if (!token.isBlank()) request.header("X-Internal-Token", token); };
        }
    }
}
