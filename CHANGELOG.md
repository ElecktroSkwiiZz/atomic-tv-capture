# Historique des versions — ATOMIC TV Capture

## 3.1.3 — Français / English

- Ajout d'un bouton **EN / FR** dans l'interface Android TV ; sélection mémorisée.
- Traduction des écrans principaux, raccourcis, contrôles d'enregistrement et messages de confirmation.
- Ressources de description d'accessibilité en anglais et nouveau README anglais.
- Identifiant et signature Android inchangés.


## 3.1.2 — Correctif icône du lanceur TV

- L’icône officielle ATOMIC TV Capture est explicitement déclarée sur l’activité Leanback, en plus de l’application.
- La bannière TV officielle est également déclarée sur l’activité de lancement, pour les launchers TCL qui utilisent ce niveau.
- Ressources visuelles renommées pour éviter les anciennes références et faciliter l’actualisation de la tuile sans désinstaller l’application.
- Identifiant Android et signature inchangés ; mise à jour compatible avec les versions officielles précédentes.


## 3.1.1 — Identité visuelle et protection des versions Release

- Nouvelle icône de l'application inspirée de l'identité officielle JB Atomic Industries.
- Nouvelle bannière Android TV compatible avec le lanceur Leanback.
- Logo de l'éditeur dans l'interface Android TV et sur le tableau de bord PC.
- Filigranes discrets dans les panneaux de l'interface PC, mentions d'éditeur dans les sources et la documentation.
- Aucune incrustation dans les captures PNG ou MP4 de l'utilisateur.
- Correction de compatibilité Java : `Intent.setClipData()` appelé séparément.
- Protection du binaire Release par R8, politique de sécurité et avertissement sur les limites de l'obfuscation.
- En-têtes CSP de protection du portail PC et favicon reprenant le logo officiel.

## 3.1.0 — 20 novembre 2026

- Nouveau nom officiel : **ATOMIC TV Capture**, édité par **JB Atomic Industries**.
- Nouvelle icône et bannière Android TV, titre harmonisé dans l'application et le tableau de bord PC.
- Dépôt de publication par défaut : `ElecktroSkwiiZz/atomic-tv-capture`.
- Fichier Release renommé en `ATOMIC-TV-Capture-AndroidTV.apk`.
- Nouvelle automatisation GitHub : les tags `vX.Y.Z` compilent une APK **signée**, vérifient le certificat et créent ensuite une Release complète.
- Identifiant Android et certificat conservés pour la compatibilité des mises à jour.

## 3.0.0 — 20 novembre 2026 (sources de développement)

- Interface Android TV compacte adaptée au D-pad.
- Touche ou combinaison configurable pour capture et vidéo.
- Tableau de bord PC, PIN 4 chiffres, téléchargement ZIP en streaming, suppression de fichiers.
- Port configurable avec recherche automatique si occupé.
- Vérification des mises à jour GitHub, contrôle de signature et installation après confirmation Android.

**Publication :** la v3.1.0 a été diffusée sur GitHub Releases ; les versions ultérieures restent soumises aux contrôles de compilation et de signature avant publication.
