# Pointage CFPM GBE AUTO 237 - Heure d'arrivée

Application Android de pointage des arrivées avec photo en direct et lecture QR Code pour le CFPM GBE AUTO 237.

## Fonctionnalités
- Enregistrement des heures d'arrivée (fuseau horaire Cameroun / Douala).
- Prise de photo obligatoire via la caméra en direct (pas d'import galerie).
- Détection automatique de l'état (À l'heure / En retard).
- Consultation du classement du jour et statistiques de présence.
- Espace Direction sécurisé par code PIN avec historique et export CSV.

## Structure du projet
- `app/src/main/java/com/gbeauto237/pointage/MainActivity.kt` : Activité principale WebView avec intégration caméra et export CSV via MediaStore.
- `app/src/main/assets/index.html` : Interface utilisateur et logique de pointage (JS + IndexedDB).
- `app/src/main/AndroidManifest.xml` : Permissions et configuration Android.

## Compilation (Android Studio)
1. Ouvrir le dossier du projet dans **Android Studio**.
2. Laisser la synchronisation Gradle se terminer.
3. Aller dans le menu : **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. L'APK généré se trouvera dans `app/build/outputs/apk/debug/app-debug.apk`.
5. Installer l'APK sur le smartphone ou la tablette.
