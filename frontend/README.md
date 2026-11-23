# NovaStore

Boutique Angular avec un catalogue public, un panier enregistré dans le navigateur, une commande après connexion et un historique des commandes.

Démarre Auth, Product, Order et Payment sur les ports 8081, 8086, 8084 et 8085. Pour que Order puisse vérifier le compte client, configure la même variable `AUTH_INTERNAL_TOKEN` dans Auth et Order. Avec Node.js et npm installés, exécute depuis ce dossier :

```powershell
npm install
npm start
```

Ouvre http://localhost:4200. Le proxy Angular distribue les requêtes `/api` aux services locaux. Lance `npm run build` pour compiler le front.

Le bouton **Simuler le paiement** utilise les statuts du service Payment sans collecter de coordonnées bancaires. Avant une mise en production, applique aussi les contrôles d’accès aux API métier.


## Connexion

Le front propose aussi la connexion, la consultation du profil et la déconnexion. Configure JWT_SECRET avant de démarrer Auth-service (voir ../Auth-service/Auth-service/README.md). Le JWT est conservé dans le stockage de session de l’onglet et supprimé à la déconnexion ou à l’expiration. Après un rechargement, le front vérifie la session auprès d’Auth-service avant d’afficher les options du compte.
