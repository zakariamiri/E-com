# Notification service

Consumes `order.created` events from the durable RabbitMQ queue `notifications.order-created` and persists processed event IDs so redeliveries do not send the same confirmation twice. The order service publishes only after its database transaction commits.

## Local setup

Start RabbitMQ (AMQP on port 5672), Eureka on port 8761, and the Config Server on port 8888, then start Notification-service on port 8083. RabbitMQ can be supplied by the team's existing infrastructure or run locally with:

```sh
docker run --rm --name ecommerce-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

The service uses a local H2 file database by default. Set `RABBITMQ_HOST`, `RABBITMQ_PORT`, `NOTIFICATION_DB_URL`, and `EUREKA_SERVER_URL` to override local defaults.

Notification-service defaults to Mailtrap Email Sandbox (`sandbox.smtp.mailtrap.io:2525`, SMTP authentication and STARTTLS enabled). It allows the sandbox's `PLAIN`, `LOGIN`, and `CRAM-MD5` authentication mechanisms. In Mailtrap, open the sandbox's **Integration → SMTP** tab and provide its credentials to the process as `SMTP_USERNAME` and `SMTP_PASSWORD`. Use the username you supplied and set `SMTP_PASSWORD` to the complete password from Mailtrap; the password value provided to Codex was masked, so it cannot be used as-is. Override `NOTIFICATION_MAIL_TO` if desired. The default recipient is a test address and sandbox SMTP captures it. The current order contract contains a customer ID but no customer email, so this configured recipient is for demonstrating the flow. To disable SMTP, set `NOTIFICATION_MAIL_ENABLED=false`; message receipt will then be logged.

## End-to-end demonstration

1. Start RabbitMQ, Eureka, Config Server, Product-service, Order-service, then Notification-service.
2. Import `postman/Order-service.postman_collection.json` and run **Créer une commande** with a valid product ID and available stock.
3. Confirm that Order-service publishes a persistent `order.created` message and Notification-service logs reception (and SMTP send if configured).
4. Redeliver the same event to `notifications.order-created` to see the duplicate ignored by the processed-event store.
