package org.sid.Notification_service.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthEventReceipt {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public AuthEventReceipt(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean record(String eventId) {
        return record(eventId, null);
    }

    public boolean record(String eventId, Long orderId) {
        return recordAndProcess(eventId, orderId, () -> {});
    }

    public boolean recordAndProcess(String eventId, Long orderId, Runnable process) {
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO processed_notifications (event_id, order_id, processed_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        """, eventId, orderId);
                process.run();
            });
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
