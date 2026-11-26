# Family Connect — partage direct Android

Cette version permet un transfert direct entre deux téléphones Android, sans compte Nextcloud, WebDAV ou service cloud.

## Utilisation

1. Connecte les deux téléphones au **même réseau Wi-Fi** (même box/routeur).
2. Sur le téléphone parent, ouvre Family Connect et choisis **Téléphone parent — recevoir les fichiers**.
3. Note l'adresse IP affichée et le code à 6 chiffres.
4. Appuie sur **Démarrer la réception** et laisse l'application ouverte sur le téléphone parent.
5. Sur le téléphone enfant, choisis **Téléphone enfant — envoyer des fichiers**, saisis l'adresse IP et le code du parent.
6. Sélectionne les fichiers à partager. Ils apparaissent dans la liste du téléphone parent, où le bouton **Ouvrir** permet de les consulter avec une application compatible.

## Limites importantes du prototype

- Le transfert fonctionne uniquement lorsque les deux téléphones sont sur le même réseau local Wi-Fi. Il ne fonctionne pas à distance via Internet ou les données mobiles.
- Le téléphone parent doit rester allumé, connecté au Wi-Fi et garder Family Connect ouvert pendant la réception.
- Le code à 6 chiffres est temporaire (il est renouvelé quand on revient sur le mode parent). Ne le communique qu'à l'appareil autorisé.
- Taille maximale d'un fichier : 50 Mo. Les fichiers sont conservés dans le stockage privé de l'application sur le téléphone parent.
- La sélection des fichiers est toujours effectuée explicitement par l'utilisateur via le sélecteur Android. Aucun accès caché aux photos ou fichiers.
- Cette version est un prototype de réseau local. Le code de connexion protège l'accès basique, mais le transfert local n'est pas chiffré. À utiliser uniquement sur un réseau Wi-Fi de confiance. Pour un usage hors domicile, il faudra ajouter un relais/serveur sécurisé ou une solution de transfert pair-à-pair avec chiffrement de bout en bout.

## Générer l'APK avec GitHub

1. Décompresse ce projet et téléverse son contenu à la racine du dépôt GitHub.
2. Ouvre l'onglet **Actions** et lance **Build Android APK** (ou pousse un commit sur `main`).
3. Quand le workflow est terminé, ouvre son exécution et télécharge l'artefact `FamilyConnect-debug`.
4. Décompresse l'artefact : `app-debug.apk` est l'APK de test à installer sur les deux appareils Android.

## Construction locale

Avec JDK 17, Android SDK et Gradle 8.9 :

```bash
gradle assembleDebug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`.
