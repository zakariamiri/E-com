package org.sid.Notification_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentNotificationTests {
    private JdbcTemplate database() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE processed_notifications (
                    event_id VARCHAR(255) PRIMARY KEY, order_id BIGINT, processed_at TIMESTAMP)
                """);
        return jdbc;
    }

    @Test
    void deduplicatesConcurrentPaymentDeliveriesAndKeepsOrderId() throws Exception {
        JdbcTemplate jdbc = database();
        var receipts = new AuthEventReceipt(jdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 16; i++) {
                results.add(executor.submit(() -> receipts.record("payment-same-event", 10L)));
            }
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(10L, jdbc.queryForObject(
                    "SELECT order_id FROM processed_notifications", Long.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void malformedPaymentEventIsRejectedBeforeRecordingReceipt() {
        JdbcTemplate jdbc = database();
        var receipts = new AuthEventReceipt(jdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()));
        var listener = new AccountPaymentListener(JsonMapper.builder().build(), receipts, mock(PaymentReceiptMailer.class));
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        assertThrows(IllegalArgumentException.class, () -> listener.onPaymentChanged(message));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_notifications", Integer.class));
    }

    @Test
    void retriesAfterSmtpFailureAndIgnoresDuplicateAfterSuccessfulSend() {
        JdbcTemplate jdbc = database();
        var receipts = new AuthEventReceipt(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
        var sender = mock(JavaMailSender.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", sender);
        var auth = mock(AuthClient.class);
        when(auth.getUserSummary(3L, "test-token")).thenReturn(new AuthClient.UserSummary("client@example.invalid"));
        var mailer = new PaymentReceiptMailer(id -> new OrderClient.OrderCustomer(3L), auth,
                beans.getBeanProvider(JavaMailSender.class), "shop@example.invalid", "test-token", true);
        var listener = new AccountPaymentListener(JsonMapper.builder().build(), receipts, mailer);
        Message success = paymentEvent("SUCCEEDED");
        doThrow(new MailSendException("SMTP en panne")).doNothing().when(sender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> listener.onPaymentChanged(success));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM processed_notifications", Integer.class));

        listener.onPaymentChanged(success);
        listener.onPaymentChanged(success);
        listener.onPaymentChanged(paymentEvent("FAILED"));

        var messages = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(2)).send(messages.capture());
        SimpleMailMessage sent = messages.getValue();
        assertArrayEquals(new String[]{"client@example.invalid"}, sent.getTo());
        assertTrue(sent.getSubject().contains("#4"));
        assertTrue(sent.getText().contains("#10"));
        assertTrue(sent.getText().contains("49.80 MAD"));
        verify(auth, times(2)).getUserSummary(3L, "test-token");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM processed_notifications", Integer.class));
    }

    private Message paymentEvent(String status) {
        String json = """
                {"eventId":"%s","eventType":"PAYMENT_STATUS_CHANGED","paymentId":4,
                 "orderId":10,"amount":49.80,"status":"%s","occurredAt":"2026-01-01T00:00:00Z"}
                """.formatted(UUID.randomUUID(), status);
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
