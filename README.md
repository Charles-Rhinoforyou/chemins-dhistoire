# Chemins d'Histoire 🏰🎧

Application Android qui **suit votre position GPS réelle** et **raconte en continu**
des récits d'Histoire sur les monuments, châteaux et personnages célèbres autour de vous.
Ton vivant, jeune, avec une pointe d'humour dosée. Gratuit, sans compte, sans frais.

## Ce que fait l'appli

- 📍 **GPS continu** : détecte les lieux historiques proches via **Wikipedia géolocalisé** (API gratuite, sans clé).
- ✍️ **Narrateur** : transforme le texte encyclopédique en récit passionné (moteur local, hors-ligne).
- 🧠 **IA locale optionnelle** : si un modèle **Gemma** (MediaPipe) est déposé dans `/files/models/`, l'appli l'utilise ; sinon repli automatique sur le narrateur local.
- 🗣️ **Voix** : synthèse vocale française du téléphone (gratuite).
- 🔄 **Enchaînement intelligent** : un récit n'est **jamais coupé** ; la file s'ajuste à la route.
- 💾 **Sauvegarde** : gardez vos récits pour les réécouter plus tard, hors connexion.
- 🚗 **Écran éteint** : lecture continue en voiture grâce à un service de premier plan.
- 🪟 **Deux modes en arrière-plan** : **mini-fenêtre flottante** (déplaçable + redimensionnable,
  par-dessus votre GPS, façon YouTube) ou **invisible** (audio seul). Bascule d'un mode à l'autre.
  Nécessite la permission « Afficher par-dessus les autres applications ».
- 🗺️ **Carte au style médiéval** (OpenStreetMap teinté parchemin, sans clé API) avec des
  **icônes de monuments façon jeu vidéo** (château, cathédrale, donjon, statue, ruine, pont, phare,
  moulin, arène romaine, mégalithe) aux vraies coordonnées. Clic → photo réelle + « Écouter ».
- 🧭 **Itinéraires scéniques optimisés** : saisissez une destination → sélection des plus beaux lieux
  du couloir de trajet (classés par intérêt), ordonnés le long de la route, avec **tracé routier réel
  (OSRM), distance et durée**.
- ⏳ **Filtre par époque** (Antiquité, Moyen Âge, Renaissance, Moderne, Contemporain) : ne garder que
  les lieux d'une période, pour l'écoute comme sur la carte.

## Déployer gratuitement (zéro coût, zéro outil à installer)

Tout se compile **dans le cloud** via GitHub Actions. Vous n'avez besoin **que d'un compte GitHub**.

### 1. Mettre le code sur GitHub
Ce dossier est **déjà un dépôt git isolé** (branche `main`, 1er commit fait). Créez un
dépôt vide sur GitHub nommé `chemins-dhistoire`, puis :
```bash
git remote add origin https://github.com/VOTRE-PSEUDO/chemins-dhistoire.git
git push -u origin main
```
> Le push sur `main` déclenche automatiquement le build de l'APK.

### 2. Laisser GitHub Actions compiler l'APK
- Onglet **Actions** de votre dépôt → le workflow **Build APK** se lance seul.
- À la fin, l'APK est disponible **(a)** en artefact téléchargeable, et **(b)** dans une **Release `latest`**.

### 3. Publier la page d'installation (GitHub Pages)
- **Settings → Pages** → *Build and deployment* → *Source* = **Deploy from a branch**.
- Branch = `main`, dossier = **`/docs`** → **Save**.
- Votre page publique : `https://VOTRE-PSEUDO.github.io/chemins-dhistoire/`
  (le bouton de téléchargement pointe automatiquement vers la dernière Release).

### 4. Installer sur le téléphone
- Ouvrez la page GitHub Pages sur Android → **Télécharger l'application** → ouvrez le `.apk`.
- Autorisez « installer depuis cette source » (normal hors Play Store).
- Ouvrez l'appli, autorisez la **localisation**, et roulez.

## Activer l'IA locale (Gemma) — optionnel, avancé

1. Récupérez un modèle compatible MediaPipe LLM Inference (`.task` ou `.bin`), ex. Gemma.
2. Placez-le dans le dossier privé de l'appli : `Android/data/com.cheminsdhistoire.app/files/models/`
   (ou via `adb push modele.task /data/data/com.cheminsdhistoire.app/files/models/`).
3. Dans l'appli, activez le commutateur **IA locale**. Sinon, le narrateur local prend le relais.

> ⚠️ L'IA locale est lourde : réservez-la aux téléphones récents. Le narrateur local
> reste le mode par défaut, rapide et universel.

## Architecture

| Fichier | Rôle |
|---|---|
| `location/LocationProvider.kt` | Flux GPS continu (FusedLocation) + Haversine |
| `data/WikipediaService.kt` | Recherche géolocalisée + extraits + images |
| `narration/TemplateNarrator.kt` | Récit vivant hors-ligne (mode par défaut) |
| `narration/LlmNarrator.kt` | IA locale Gemma (réflexion, repli auto) |
| `audio/SpeechManager.kt` | Synthèse vocale FR, suivi par segment |
| `data/JourneyStore.kt` | Sauvegarde JSON des récits |
| `playback/PlaybackController.kt` | Le « cerveau » : file, enchaînement sans coupure |
| `service/PlaybackService.kt` | Service de premier plan (écran éteint) |
| `ui/CheminsApp.kt` | Interface Jetpack Compose |

## Sources & licence

Contenu issu de **Wikipédia** (CC BY-SA). Projet éducatif. Aucune donnée personnelle
n'est envoyée : la position sert uniquement à interroger l'API Wikipedia.
