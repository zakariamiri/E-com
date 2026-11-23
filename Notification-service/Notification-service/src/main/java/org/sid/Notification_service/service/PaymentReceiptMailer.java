package org.sid.Notification_service.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PaymentReceiptMailer {
    private final OrderClient orders;
    private final AuthClient accounts;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String internalToken;
    private final boolean enabled;

    public PaymentReceiptMailer(OrderClient orders, AuthClient accounts,
                                ObjectProvider<JavaMailSender> mailSender,
                                @Value("${notification.mail.from:no-reply@example.com}") String from,
                                @Value("${AUTH_INTERNAL_TOKEN:}") String internalToken,
                                @Value("${notification.mail.enabled:false}") boolean enabled) {
        this.orders = orders;
        this.accounts = accounts;
        this.mailSender = mailSender;
        this.from = from;
        this.internalToken = internalToken;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void send(AccountPaymentListener.PaymentStatusChangedEvent event) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || internalToken.isBlank()) {
            throw new IllegalStateException("Les reçus nécessitent un expéditeur SMTP et AUTH_INTERNAL_TOKEN.");
        }
        var order = orders.getOrder(event.orderId());
        if (order == null || order.customerId() == null) {
            throw new IllegalStateException("Client introuvable pour la commande " + event.orderId());
        }
        var customer = accounts.getUserSummary(order.customerId(), internalToken);
        if (customer == null || customer.email() == null || customer.email().isBlank()) {
            throw new IllegalStateException("Adresse e-mail introuvable pour la commande " + event.orderId());
        }

        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(customer.email());
        message.setSubject("NovaStore | Reçu de paiement #" + event.paymentId());
        message.setText("""
                Bonjour,

                Votre paiement a été confirmé.
                Commande : #%d
                Paiement : #%d
                Montant : %s MAD

                Merci pour votre commande.
                """.formatted(event.orderId(), event.paymentId(), event.amount().toPlainString()));
        sender.send(message);
    }
}
