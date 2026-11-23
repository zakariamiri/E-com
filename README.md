# Démarrer Auth et Payment en local

Le projet utilise Java 17 ou supérieur, PostgreSQL et RabbitMQ. Dans IntelliJ, importe les fichiers Maven des deux modules et choisis un JDK compatible. Si IntelliJ ne trouve pas les classes Resilience4j de Payment après un pull, utilise **Maven → Reload All Maven Projects**.

## Créer les bases avec Docker Compose

Depuis la racine du dépôt, crée `.env` à partir de [.env.example](.env.example) et renseigne les quatre mots de passe. Ouvre Docker Desktop, puis lance :

```powershell
docker compose up -d --wait
docker compose ps
```

Au premier démarrage, PostgreSQL crée `authdb` et `paymentdb`, avec les rôles `auth_app` et `payment_app`. Le script V6 initialise aussi les tables et les index Payment. Auth crée ses tables au premier démarrage avec le profil `local`.

Dans IntelliJ, utilise les valeurs suivantes et les mots de passe du fichier `.env`.

| Configuration | Valeurs avec les ports par défaut de Compose |
|---|---|
| Auth | `AUTH_DB_URL=jdbc:postgresql://localhost:55432/authdb`, `AUTH_DB_USERNAME=auth_app`, `AUTH_DB_PASSWORD` et `JWT_SECRET`. |
| Payment | `PAYMENT_DB_URL=jdbc:postgresql://localhost:55432/paymentdb`, `PAYMENT_DB_USERNAME=payment_app` et `PAYMENT_DB_PASSWORD`. |
| Auth, Payment et les autres services qui partagent le broker | `RABBITMQ_HOST=localhost`, `RABBITMQ_PORT=5673`, `RABBITMQ_USERNAME=novastore` et `RABBITMQ_PASSWORD`. Si tu modifies `RABBITMQ_USER` dans `.env`, reporte cette valeur dans `RABBITMQ_USERNAME`. |

Les ports Docker diffèrent des ports natifs pour pouvoir cohabiter avec un PostgreSQL ou un RabbitMQ déjà installé. `docker compose down` arrête les conteneurs et conserve les volumes. Les mots de passe PostgreSQL et RabbitMQ sont définis lors de l’initialisation de leurs volumes.

## Configuration IntelliJ

Les sections suivantes utilisent le port PostgreSQL natif `5432`. Avec Compose, reprends les URL sur `55432` ci-dessus.

### AuthService sur le port 8081

Crée ou réutilise la base PostgreSQL `authdb` et un rôle autorisé à y créer et modifier les tables. Dans **Run → Edit Configurations → AuthServiceApplication**, configure le profil actif `local` pour démarrer sans Config Server ni Eureka, puis renseigne les variables suivantes.

| Variable | Valeur locale |
|---|---|
| `AUTH_DB_URL` | `jdbc:postgresql://localhost:5432/authdb`. Adapte le port si ton PostgreSQL écoute sur un autre port. |
| `AUTH_DB_USERNAME` | Ton rôle PostgreSQL, par exemple `auth_app`. |
| `AUTH_DB_PASSWORD` | Le mot de passe de ce rôle. |
| `JWT_SECRET` | Une clé Base64 contenant au moins 32 octets aléatoires. Garde la même clé entre les redémarrages. |
| `AUTH_INTERNAL_TOKEN` | Une autre clé partagée avec Order, Cart et Notification si ces services utilisent le résumé utilisateur. |

Pour générer une clé localement dans PowerShell 7, exécute `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`. Pour `AUTH_INTERNAL_TOKEN`, génère une deuxième valeur distincte. Renseigne les valeurs dans IntelliJ.

### PaymentService sur le port 8085

Crée ou réutilise la base PostgreSQL `paymentdb` et un rôle autorisé à y créer et modifier les tables. Dans **Run → Edit Configurations → PaymentServiceApplication**, configure le profil actif `local`, puis renseigne les variables suivantes.

| Variable | Valeur locale |
|---|---|
| `PAYMENT_DB_URL` | `jdbc:postgresql://localhost:5432/paymentdb`. Adapte le port PostgreSQL si nécessaire. |
| `PAYMENT_DB_USERNAME` | Ton rôle PostgreSQL, par exemple `payment_app`. |
| `PAYMENT_DB_PASSWORD` | Le mot de passe de ce rôle. |
| `ORDER_SERVICE_URL` | `http://localhost:8084` si OrderService tourne sur la même machine. |

Si tu utilises PostgreSQL directement, exécute le script [payment-v6.sql](Payment-service/Payment-service/src/main/resources/db/payment-v6.sql) sur `paymentdb` avec le rôle propriétaire. Il prépare la table `payments`, les index d’idempotence et l’outbox. Docker Compose exécute déjà ce script sur un volume neuf.

Lance RabbitMQ sur `localhost:5672` pour les événements V5, ou configure `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` et `RABBITMQ_PASSWORD` dans les services concernés.

## Reconnaître l’erreur de démarrage

| Message ou symptôme | Réglage à vérifier |
|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | Ajoute la clé dans la configuration d’AuthServiceApplication. |
| `Could not resolve placeholder 'PAYMENT_DB_PASSWORD'` | Ajoute le mot de passe dans la configuration de PaymentServiceApplication. |
| `Connection refused` vers `5432` ou `5433` | Démarre PostgreSQL et vérifie le port dans l’URL JDBC du service concerné. |
| `password authentication failed` ou `database ... does not exist` | Vérifie le rôle, son mot de passe et le nom de la base dans pgAdmin ou PostgreSQL. |
| Payment démarre mais le premier paiement échoue avec une erreur `ON CONFLICT` | Applique le script `payment-v6.sql` dans `paymentdb`. |

Vérifie ensuite l’état des services avec `GET http://localhost:8081/actuator/health` et `GET http://localhost:8085/actuator/health`.
