# Hand Com

Outil de communication pour un club de handball : on prépare les posts Instagram et TikTok à l'avance, et ils partent tout seuls à l'heure prévue.

- **Calendrier** mensuel des posts (programmés, publiés, en échec), avec la liste des brouillons
- **Modèles hand** : « Jour de match », « Résultat », « Convocation », qui génèrent le texte à partir de l'adversaire, du score, de la salle, etc.
- **Instagram** : photo, carrousel (2 à 10 médias) ou Reel, publiés automatiquement
- **TikTok** : la vidéo arrive en brouillon dans l'app TikTok, vous la publiez d'un tap (voir plus bas pourquoi)
- PNG converti automatiquement en JPEG (seul format accepté par Instagram)
- Renouvellement automatique des jetons, historique des publications, relance d'un post en échec

Stack : Angular 21 (Bun), Spring Boot 4 (Java 21), PostgreSQL 17, Docker Compose.

---

## 1. Lancer le projet

### Option A : tout en Docker

```bash
cp .env.example .env      # puis changez au minimum ADMIN_PASSWORD
docker compose up -d --build
```

- Interface : http://localhost:4200 (identifiant `admin` et le mot de passe du `.env`)
- API : http://localhost:8080

### Option B : en développement (rechargement à chaud)

```bash
docker compose up -d db                 # PostgreSQL seul, sur le port 5442
cd backend && ./mvnw spring-boot:run    # lit automatiquement ../.env
cd frontend && bun install && bun start # http://localhost:4200, avec proxy vers :8080
```

> Le port de la base sur votre machine est **5442** (5432 et 5433 étaient déjà occupés). Vous pouvez le changer avec `DB_PORT` dans `.env`.

> La CLI Angular 22 demande Node ≥ 22.22 et votre Node système est en 22.19, d'où Angular 21. Si vous mettez Node à jour, la migration se fait avec `bunx ng update @angular/core@22 @angular/cli@22`.

---

## 2. Rendre le serveur joignable depuis Internet (tunnel)

Instagram et TikTok doivent pouvoir **revenir sur votre serveur** après la connexion d'un compte (OAuth). Instagram doit aussi **télécharger les photos et vidéos** depuis une URL publique en HTTPS. En local, il faut donc un tunnel vers le port 8080.

**Recommandé : ngrok**, qui offre gratuitement un domaine fixe, ce qui évite de reconfigurer les apps à chaque redémarrage.

1. Créez un compte sur ngrok.com et réservez votre domaine gratuit (menu *Domains*).
2. `ngrok http --url=votre-domaine.ngrok-free.app 8080`
3. Dans `.env` : `PUBLIC_BASE_URL=https://votre-domaine.ngrok-free.app`
4. Redémarrez le backend (`docker compose up -d handcom-backend` ou relancez `./mvnw`).

La page **Comptes** affiche un avertissement tant que l'URL n'est pas en HTTPS public, et donne les URL de redirection exactes à copier dans les apps.

> Seuls `/media/**` (fichiers à usage public, aux noms aléatoires) et les retours OAuth sont accessibles sans mot de passe. Tout le reste demande l'identifiant admin.

---

### Sur le serveur (Traefik)

Le fichier `docker-compose.prod.yml` publie le site sur **https://hand-com.elearnchess.com** via Traefik (réseau externe `web`, entrypoint `websecure`). Aucun port n'est ouvert sur l'hôte, et le tunnel ngrok devient inutile.

```bash
git clone git@github.com:rmetivier/hand-com.git && cd hand-com
cp .env.example .env      # changez ADMIN_PASSWORD et DB_PASSWORD, ajoutez les clés Instagram/TikTok
docker compose -f docker-compose.prod.yml up -d --build
```

Mise à jour : `git pull && docker compose -f docker-compose.prod.yml up -d --build`

`PUBLIC_BASE_URL` et `FRONTEND_URL` sont fixées dans `docker-compose.prod.yml`. Les URL de redirection à déclarer dans les apps deviennent :
`https://hand-com.elearnchess.com/api/oauth/instagram/callback` et `https://hand-com.elearnchess.com/api/oauth/tiktok/callback`.

---

## 3. Créer les apps (une seule fois)

### Instagram

Prérequis : le compte Instagram du club doit être **professionnel** (app Instagram > Paramètres > Type de compte > Créateur ou Entreprise). Une page Facebook n'est **pas** nécessaire.

1. Sur developers.facebook.com, cliquez sur *Créer une app*, choisissez le cas d'usage **« Gérer les messages et le contenu sur Instagram »**, puis le type *Entreprise*.
2. Dans *Instagram > Configuration de l'API avec la connexion Instagram* :
   - Notez l'**ID de l'app Instagram** et la **clé secrète** : ce sont `INSTAGRAM_CLIENT_ID` et `INSTAGRAM_CLIENT_SECRET`.
   - Dans *Configurer la connexion professionnelle Instagram*, ajoutez l'URL de redirection : `https://votre-domaine.ngrok-free.app/api/oauth/instagram/callback`
3. Dans *Rôles de l'app > Rôles*, ajoutez le compte Instagram du club comme **testeur Instagram**, puis acceptez l'invitation depuis l'app Instagram (Paramètres > Apps et sites web > Invitations de testeur).
4. Tant que l'app reste en **mode développement**, elle peut publier sur les comptes testeurs. Pour le club, c'est suffisant : pas besoin de la validation Meta.

### TikTok

1. Sur developers.tiktok.com, cliquez sur *Manage apps*, puis *Connect an app*.
2. Ajoutez les produits **Login Kit** et **Content Posting API**, avec les scopes `user.info.basic` et `video.upload`.
3. Dans Login Kit, déclarez l'URL de redirection : `https://votre-domaine.ngrok-free.app/api/oauth/tiktok/callback`
4. Notez la **Client key** et le **Client secret** : ce sont `TIKTOK_CLIENT_KEY` et `TIKTOK_CLIENT_SECRET`.
5. En mode *Sandbox*, ajoutez le compte TikTok du club comme utilisateur cible (*Sandbox settings > Target users*).

**Pourquoi un brouillon et pas une publication directe ?** TikTok n'autorise la publication publique directe qu'aux apps qui ont passé son audit, difficile à obtenir pour un outil perso. Sans audit, les vidéos publiées directement resteraient en privé. Le mode brouillon fonctionne, lui, sans audit : la vidéo arrive dans les notifications de l'app TikTok, vous ajoutez le texte (bouton « Copier le texte » dans l'éditeur) et la musique, puis vous publiez.

### Ensuite

Renseignez les 4 valeurs dans `.env`, redémarrez le backend, puis allez sur **Comptes** et cliquez sur *Connecter le compte*.

---

## 4. Limites à connaître

| | Instagram | TikTok |
|---|---|---|
| Formats | JPEG (PNG converti), MP4/MOV | MP4/MOV, une seule vidéo |
| Ratio | 4:5 à 1.91:1 pour les photos, 9:16 conseillé pour les Reels | 9:16 conseillé |
| Texte | 2200 caractères, 30 hashtags max | Saisi dans l'app au moment de publier |
| Volume | 100 publications par 24 h via l'API | Quelques envois par jour en sandbox |
| Jetons | 60 jours, renouvelés automatiquement à chaque publication | 24 h, renouvelés automatiquement (365 jours max) |

- **Le serveur doit tourner à l'heure prévue** : si votre PC est éteint, le post partira au prochain démarrage. Pour un usage régulier, pensez à un petit serveur (Raspberry Pi, VPS à quelques euros).
- Un post « Partiel » (réussi sur un réseau, raté sur l'autre) peut être relancé : seul le réseau en échec est retenté.
- Les jetons sont stockés en clair dans PostgreSQL. Gardez la base et le fichier `.env` privés.

---

## 5. Structure

```
backend/   Spring Boot : API REST, planificateur, intégrations Instagram et TikTok
  config/    sécurité (HTTP Basic), propriétés, service des médias
  media/     upload et conversion des fichiers
  post/      posts, règles de validation par réseau
  social/    comptes connectés, OAuth, clients instagram/ et tiktok/
  publish/   planificateur (toutes les 15 s)
frontend/  Angular : calendrier, éditeur avec modèles hand, page Comptes
docker-compose.yml   db + backend + frontend (nginx)
```

Tests : `cd backend && ./mvnw test` et `cd frontend && bun run test`.

Personnalisation : la couleur principale est la variable `--accent` dans `frontend/src/styles.scss`, à remplacer par celle du club. Le texte des modèles se modifie dans `frontend/src/app/core/templates.ts`.
