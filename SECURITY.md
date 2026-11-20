# Politique de sécurité

Pour signaler une faille de sécurité dans ATOMIC TV Capture, contactez JB Atomic Industries via https://jbatomic.com/. N'ajoutez pas de détails d'exploitation, de clé privée ou de données d'utilisateurs dans une issue publique.

Le portail PC est un serveur **HTTP local non chiffré**. Il doit rester accessible uniquement depuis un réseau local de confiance, sans redirection de port sur Internet. Il demande un PIN à 4 chiffres, applique une limitation des tentatives et protège les actions par session et jeton CSRF. Ne transférez pas de captures sensibles sur un réseau partagé ou non fiable.

Les APK Release sont signées avec une clé privée stockée hors du dépôt via GitHub Actions Secrets, et l'application vérifie la signature lors des mises à jour. La clé et les mots de passe ne doivent jamais être publiés.
