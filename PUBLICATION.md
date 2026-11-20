# Publier ATOMIC TV Capture sur GitHub et proposer les mises à jour automatiques

**Projet : ATOMIC TV Capture** · Éditeur : **JB Atomic Industries** · https://jbatomic.com/

Le dépôt cible est `ElecktroSkwiiZz/atomic-tv-capture` et l'APK publiée s'appellera **`ATOMIC-TV-Capture-AndroidTV.apk`**. Le package Android reste **`com.jbatomic.tvcapture`** afin de maintenir les mises à jour.

> **Attention :** la clé privée fournie séparément reste confidentielle. Ne l'envoyez jamais sur GitHub ni dans une archive publique. Les secrets GitHub Actions remplacent une clé stockée dans le dépôt.

## 1 — Créer le dépôt GitHub

1. Ouvrir https://github.com/new en étant connecté à `ElecktroSkwiiZz`.
2. Nom du dépôt : `atomic-tv-capture` ; visibilité : **Public** (nécessaire pour l'accès anonyme à l'API Releases).
3. Laisser décochés **Add a README file**, **Add .gitignore**, **Choose a license** : ces éléments se trouvent déjà dans les sources ou seront choisis ensuite.
4. Valider avec **Create repository**.
5. Décompresser le ZIP de sources fourni. **Ne jamais décompresser l'archive de signature privée dans le dossier public du projet.**

Pour transférer le projet sous Windows PowerShell, ouvrir un terminal dans le dossier racine du projet (celui qui contient `README.md`, `app`, `.github`) et lancer :

```powershell
# Après avoir créé le dépôt vide sur GitHub :
git init
git branch -M main
git add .
git commit -m "Initial release: ATOMIC TV Capture 3.1.0"
git remote add origin https://github.com/ElecktroSkwiiZz/atomic-tv-capture.git
git push -u origin main
```

Si GitHub demande une connexion, authentifiez-vous via le navigateur / gestionnaire d'identifiants Git. **Ne collez pas le mot de passe de votre compte dans une commande.**

> Les fichiers `.github/workflows/android-apk.yml` sont indispensables à la compilation. Dans un dossier Windows, pensez à afficher les éléments masqués pour voir `.github` et `.gitignore`.

## 2 — Enregistrer la clé de signature dans les secrets GitHub

Le certificat public de référence est dans `SIGNATURE_PUBLIQUE.txt`. Conservez le fichier privé `TV-Capture-Release.jks` dans un emplacement sécurisé **hors du dépôt**, avec ses mots de passe (archive privée fournie précédemment).

Ouvrir le dépôt GitHub : **Settings → Secrets and variables → Actions → New repository secret**. Créer exactement les quatre secrets suivants :

| Secret GitHub | Valeur à renseigner |
| --- | --- |
| `TV_CAPTURE_KEYSTORE_BASE64` | Chaîne base64 du contenu du fichier `.jks` |
| `TV_CAPTURE_STORE_PASSWORD` | Mot de passe du magasin de clés |
| `TV_CAPTURE_KEY_PASSWORD` | Mot de passe de la clé (identique si la clé a été créée avec le même) |
| `TV_CAPTURE_KEY_ALIAS` | `tv-capture` |

Pour récupérer **uniquement la chaîne base64 à copier** depuis PowerShell dans le dossier contenant la clé privée :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.\TV-Capture-Release.jks')))
```

**Attention :** ne collez pas cette chaîne dans une conversation, un ticket, une capture publique, un README ou un fichier du dépôt. Elle donne accès à la clé privée. Effectuez le collage directement dans le champ chiffré GitHub Secrets. Ne sauvegardez pas la chaîne dans l'historique Git.

Le workflow contrôle automatiquement que le certificat ayant signé l'APK correspond exactement à l'empreinte publique fournie ; une autre clé entraîne un échec et empêche la publication de la mauvaise APK.

## 3 — Vérifier la compilation automatique

Le premier `git push` vers `main` lance **Actions → ATOMIC TV Capture - Build & Release APK** et produit une **APK DEBUG de test**, dans la section **Artifacts** de l'exécution.

Cette APK de test n'est **pas** une Release officielle et n'utilise pas nécessairement la même signature. Ne la diffusez pas comme version stable ; les mises à jour depuis une version debug vers une Release peuvent échouer.

## 4 — Publier la première APK Release officielle

Vérifier que `app/build.gradle` contient **`versionCode 30100`** et **`versionName '3.1.0'`**. Sur le PC, à la racine du dépôt, une fois le code poussé :

```powershell
git tag v3.1.0
git push origin v3.1.0
```

Le tag **`v3.1.0`** déclenche automatiquement une autre exécution GitHub Actions. Celle-ci :

1. vérifie que le tag correspond à la version Android ;
2. restaure la clé de signature depuis les secrets ;
3. compile une APK Release signée ;
4. contrôle le certificat et calcule le SHA-256 ;
5. **crée la GitHub Release avec les fichiers** `ATOMIC-TV-Capture-AndroidTV.apk` et `.apk.sha256`.

La Release est créée **après** la compilation, pour ne jamais afficher une version publique sans APK prête. Vérifier que le job est vert dans **Actions**, puis visiter **Releases** et contrôler la présence des deux fichiers. Si le job échoue, consulter les logs et **ne pas distribuer d'APK debug** à la place.

## 5 — Publier une mise à jour (exemple 3.1.1)

Modifier dans `app/build.gradle` :

```gradle
versionCode 30101
versionName '3.1.1'
```

Après avoir modifié le code et mis à jour le CHANGELOG :

```powershell
git add .
git commit -m "Release ATOMIC TV Capture 3.1.1"
git push origin main
git tag v3.1.1
git push origin v3.1.1
```

Le même workflow construira automatiquement une nouvelle APK **signée avec la même clé** et créera la Release `v3.1.1`.

**Règle obligatoire :** le `versionCode` doit augmenter à chaque mise à jour, et le certificat de signature doit rester le même. Ne recréez pas une nouvelle clé. Faites une sauvegarde chiffrée, idéalement sur deux supports différents, de la clé et de ses mots de passe.

## 6 — Comment la TV reçoit les mises à jour

L'application vise par défaut le dépôt public **`ElecktroSkwiiZz/atomic-tv-capture`**. Dans **Mises à jour**, on peut lancer **Rechercher une mise à jour sur GitHub**.

- À chaque ouverture de l'application, elle vérifie si au moins 24 heures se sont écoulées depuis sa dernière vérification. Ce réglage est désactivable ; **ce n'est pas une tâche quotidienne exécutée en permanence en arrière-plan**.
- Lorsque GitHub publie une version stable plus récente, l'application propose de télécharger l'APK.
- Le téléchargement provient de la GitHub Release ; l'application vérifie la somme SHA-256 quand GitHub fournit son empreinte, puis le **nom du package**, le **certificat de signature** et le **numéro de version**.
- Android affiche la demande d'autorisation d'installation et réclame la confirmation de l'utilisateur. **Une installation entièrement silencieuse n'est pas possible pour cette application classique.**

Si vous changez de dépôt après publication, modifiez le dépôt configuré dans l'application déjà installée, ou publiez une version passerelle qui utilise la nouvelle adresse.

**Migration depuis l'ancienne APK debug :** sauvegarder d'abord les captures depuis le navigateur ou ADB. Si la clé de signature est différente, Android refusera `install -r` : désinstaller l'ancienne version (les fichiers locaux peuvent disparaître), puis installer la première Release officielle. Les Releases suivantes se mettront à jour en place.

## 7 — Télécharger depuis le site JB Atomic Industries

Sur https://jbatomic.com/ : publier une page produit nommée **ATOMIC TV Capture**, avec la mention **pour Android TV / Google TV**, les fonctions principales, la version minimale Android 11 et un lien vers la dernière GitHub Release officielle.

Une fois le dépôt et la Release créés, l'adresse de téléchargement direct suivra le modèle :

```text
https://github.com/ElecktroSkwiiZz/atomic-tv-capture/releases/latest/download/ATOMIC-TV-Capture-AndroidTV.apk
```

**Ne pas annoncer cette adresse comme opérationnelle avant l'existence effective d'une Release signée.** Fournir aussi les limitations sur le HDMI et les contenus DRM et un lien vers `PRIVACY.md`.

## 8 — Contrôles recommandés avant diffusion

- Compiler avec GitHub Actions et vérifier la signature de l'APK finale (`apksigner verify --print-certs`).
- Installer sur la TCL et vérifier capture, enregistrement, transferts ZIP, suppression individuelle et totale et port alternatif (8766/8767).
- Vérifier les combinaisons selon les touches réellement transmises par la télécommande TCL.
- Tester une mise à jour 3.1.0 → 3.1.1 avec la **même clé** ; confirmer que les captures existantes restent disponibles.
- Ne jamais publier le keystore ou ses mots de passe. Choisir une licence publique adaptée si vous souhaitez autoriser la redistribution du code.
