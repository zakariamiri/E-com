package org.sid.Notification_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AuthEventReceiptTests {
    @Test
    void recordsOnlyOneReceiptForConcurrentDeliveries() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE processed_notifications (
                    event_id VARCHAR(255) PRIMARY KEY,
                    order_id BIGINT,
                    processed_at TIMESTAMP
                )
                """);
        var receipts = new AuthEventReceipt(jdbc, new DataSourceTransactionManager(source));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 16; i++) {
                results.add(executor.submit(() -> receipts.record("auth-same-event")));
            }
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM processed_notifications", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }
}
