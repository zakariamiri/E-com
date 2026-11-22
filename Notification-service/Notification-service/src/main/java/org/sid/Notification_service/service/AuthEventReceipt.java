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
        try {
            transactions.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO processed_notifications (event_id, order_id, processed_at)
                    VALUES (?, NULL, CURRENT_TIMESTAMP)
                    """, eventId));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
