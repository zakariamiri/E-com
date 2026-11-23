# Auth-service : inscription et connexion

Java 17, PostgreSQL et RabbitMQ sont utilisés par le service. La base PostgreSQL et son utilisateur doivent déjà exister. Hibernate crée/met à jour les tables ; le script `src/main/resources/db/auth-v6-outbox.sql` contient aussi les droits et l'index de l'outbox.

## Configuration locale

Dans la configuration Run/Debug d'IntelliJ, définir :

| Variable | Valeur |
| --- | --- |
| `AUTH_DB_URL` | URL JDBC de la base locale, par exemple `jdbc:postgresql://localhost:5432/authdb` |
| `AUTH_DB_USERNAME` | `auth_app`, ou votre utilisateur local |
| `AUTH_DB_PASSWORD` | Votre mot de passe PostgreSQL |
| `JWT_SECRET` | Clé aléatoire de 32 octets minimum, encodée en Base64 |
| `AUTH_INTERNAL_TOKEN` | Clé aléatoire partagée uniquement avec Order et Cart |

Générer **deux valeurs distinctes** avec cette commande PowerShell (l'exécuter deux fois) :

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Conserver ces valeurs dans les variables d'environnement locales ; ne pas les enregistrer dans Git. Ne pas régénérer JWT_SECRET à chaque requête : son remplacement invalide tous les JWT existants. L'absence ou une clé JWT trop courte empêche le démarrage.

Configurer la **même valeur AUTH_INTERNAL_TOKEN** dans les configurations IntelliJ d'Auth, Order et Cart, puis redémarrer ces services. Sans cette variable, les appels internes au résumé utilisateur sont refusés. Les modifications des clients Feign associées doivent être intégrées avec cette évolution d'Auth.

## API

| Requête | Accès |
| --- | --- |
| `POST /api/users` | Public : inscription, rôle USER imposé côté serveur |
| `POST /api/auth/login` | Public : email et mot de passe |
| `GET /api/auth/me` | JWT : profil connecté |
| `GET /api/users` | ADMIN |
| `GET, PUT, DELETE /api/users/{id}` | Propriétaire ou ADMIN |
| `GET /api/users/{id}/summary` | Propriétaire, ADMIN, ou clé interne valide |

Exemple de connexion :

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{"email":"rim@example.com","password":"votre-mot-de-passe"}
```

La réponse contient `accessToken`, `tokenType` (Bearer), `expiresIn` (900 secondes) et `user` sans mot de passe. Pour les routes protégées, ajouter `Authorization: Bearer <accessToken>`. Une absence de JWT, une signature invalide, un JWT expiré, ou un compte supprimé/désactivé donne 401 ; des droits insuffisants donnent 403.

La clé interne utilise `X-Internal-Token` et ne donne accès qu'à `GET /api/users/{id}/summary`, jamais aux autres opérations. Cette clé donne accès aux résumés de tous les utilisateurs : ne pas la transmettre au navigateur.

## Frontend et tests

Depuis `frontend`, lancer `npm install` puis `npm start` et ouvrir http://localhost:4200. L'écran permet l'inscription, la connexion, la lecture du profil et la déconnexion. Le JWT reste en mémoire ; recharger la page ou se déconnecter le supprime côté navigateur. Un JWT copié reste valide jusqu'à son expiration ; il n'y a pas de révocation individuelle ni de refresh token.

Depuis ce module : `mvn test`. Les tests utilisent H2 en mémoire, un serveur HTTP aléatoire et des clés de test. Ils ne modifient pas PostgreSQL et ne nécessitent pas RabbitMQ. Le test d'outbox vérifie séparément les nouvelles tentatives d'envoi. Ces tests ne remplacent pas un test d'intégration réel avec PostgreSQL, RabbitMQ et les autres services.

Importer `postman/Auth-security.postman_collection.json` à la racine du dépôt et exécuter les requêtes dans l'ordre. Les anciens appels Postman de lecture/modification nécessitent désormais le Bearer token.

## Administration et limites

Aucun compte ADMIN ni mot de passe administrateur par défaut n'est créé. Un administrateur de la base peut attribuer le rôle ADMIN à un compte existant via `auth_roles` et `auth_user_roles`, puis ce compte doit se reconnecter. Une inscription publique ne permet pas de choisir son rôle. Un rôle retiré d'un compte reste dans un JWT déjà émis jusqu'à son expiration (15 minutes).

Cette évolution protège **Auth-service**. Les API métier de Payment, Order et Cart doivent disposer de leur propre protection/validation des JWT ; elles ne deviennent pas sécurisées automatiquement. Le JWT émis ici est destiné à l'API Auth (`aud=novastore-auth-api`). La récupération de mot de passe, la vérification d'email, la limitation des tentatives de connexion et le MFA ne sont pas inclus. Utiliser HTTPS pour tout déploiement hors localhost.
