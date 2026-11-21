<div align="center">

![ATOMIC TV Capture — JB Atomic Industries](branding/atomic-tv-capture-banner.webp)

# ATOMIC TV Capture
### Captures d’écran, enregistrement vidéo et transfert de fichiers sur Android TV

**Android TV / Google TV · Captures PNG · Vidéo MP4 · Interface web PC**

[Français](README.md) · [English](README.en.md) · [Español](README.es.md) · [Русский](README.ru.md)

[**Télécharger l’APK officielle**](https://github.com/ElecktroSkwiiZz/atomic-tv-capture/releases/latest) · [Toutes les versions](https://github.com/ElecktroSkwiiZz/atomic-tv-capture/releases) · [Page du logiciel](https://jbatomic.com/atomic-tv-capture/)

</div>

## Présentation

**ATOMIC TV Capture** est une application de **capture d’écran Android TV et Google TV** développée par **JB Atomic Industries**. Elle permet de déclencher des captures avec la télécommande, d’enregistrer l’écran Android et de récupérer les fichiers directement depuis le navigateur d’un ordinateur ou d’un smartphone connecté au même réseau local.

## Fonctionnalités

| Fonction | Description |
| --- | --- |
| **Captures PNG** | Capture via appui long ou combinaison configurable de touches de télécommande |
| **Vidéo MP4** | Enregistrement H.264 jusqu’à 1080p / 30 FPS, **sans audio** |
| **Contrôle depuis le navigateur** | Déclenchement et consultation depuis un PC ou téléphone sur le réseau local |
| **Transfert de fichiers** | Téléchargement individuel ou ZIP ; gestion et suppression avec confirmation |
| **Connexion PIN** | PIN à 4 chiffres, limitation des tentatives et contrôle de session |
| **Port local configurable** | Port 8766 par défaut, recherche d’une alternative si nécessaire |
| **Interface Android TV** | Navigation télécommande et langues français / anglais |
| **Mises à jour** | APK officielles GitHub Releases avec vérification de la signature |

## Télécharger et installer

**Dernière version publiée : v3.1.3.** [Télécharger l’APK Android TV signée](https://github.com/ElecktroSkwiiZz/atomic-tv-capture/releases/latest).

1. Installez la version officielle sur **Android TV / Google TV Android 11+**.
2. Depuis la TV, ouvrez **Paramètres → Accessibilité** et activez les raccourcis **ATOMIC TV Capture**.
3. Dans l’application, configurez la touche ou la combinaison de capture.
4. Pour accéder aux fichiers depuis un navigateur, relevez l’URL locale et le PIN affichés à l’écran.
5. Ouvrez cette adresse depuis un appareil sur le **même réseau privé de confiance**.

## Ce qu’ATOMIC TV Capture peut — et ne peut pas — enregistrer

- Capture du **contenu affiché par le système Android**, dans les limites des autorisations disponibles.
- La vidéo utilise les autorisations Android de capture d’écran ; **une confirmation est nécessaire pour chaque session**.
- Les fichiers MP4 sont **sans piste audio**.
- Les contenus **DRM / FLAG_SECURE** peuvent apparaître noirs ou être refusés.
- **Le signal HDMI d’une console externe** (Xbox, PlayStation, etc.) n’est généralement **pas accessible** aux API de capture Android TV.
- Certaines touches réservées au système ne peuvent pas être interceptées.
- Les fichiers PNG et MP4 exportés ne contiennent **aucun filigrane ajouté par défaut**.

## Questions fréquentes

**Peut-on faire une capture d’écran Android TV sans ordinateur ?** Oui, en utilisant les raccourcis de télécommande après configuration de l’accessibilité.

**Peut-on récupérer les captures sur Windows ou un smartphone ?** Oui, par le tableau de bord web local et le téléchargement des fichiers.

**Peut-on enregistrer une console branchée en HDMI ?** Non, cette fonction n’est généralement pas possible avec les API de capture d’écran Android.

**Le son est-il enregistré ?** Non. La vidéo MP4 est enregistrée sans audio.

**Faut-il une connexion Internet ?** Le transfert web fonctionne sur le réseau local ; la recherche de mises à jour nécessite un accès à GitHub.

## Sécurité, publication et développement

Le portail utilise **HTTP local non chiffré** : ne l’utilisez que sur un réseau privé de confiance et **ne redirigez jamais son port sur Internet**.

**Application ID :** `com.jbatomic.tvcapture` · **Android :** API 30+ · **Compilation :** JDK 17, Android SDK 35 et Gradle 8.10.2 (`gradle --no-daemon assembleDebug`).

[Documentation anglaise](README.en.md) · [Historique](CHANGELOG.md) · [Sécurité](SECURITY.md) · [Confidentialité](PRIVACY.md) · [Guide de publication](PUBLICATION.md) · [Copyright](COPYRIGHT.md).

[Découvrez aussi ATOMIC TV Console — commandes ADB pour Android TV](https://github.com/ElecktroSkwiiZz/atomic-tv-console).

© 2026 **[JB Atomic Industries](https://jbatomic.com/atomic-tv-capture/)**. Aucune licence de redistribution ou de réutilisation du code n’est accordée par défaut.
