# Focus — règles du projet

Développement 100 % vibecoding : l'utilisateur n'édite pas le code. Communiquer en français, réponses courtes.

## Règles permanentes
1. Ne jamais modifier l'applicationId `com.alexandre.focus`, `debug.keystore`, l'alias, les mots de passe ou la config de signature sans autorisation explicite.
2. Préserver les données utilisateur à chaque mise à jour (voir « Données »).
3. Conserver la compilation APK via GitHub Actions (`.github/workflows/build.yml`).
4. Incrémenter `versionCode` (+1) et `versionName` à chaque nouvelle version.
5. Ne jamais committer de nouveaux secrets ou clés privées. Utiliser les GitHub Secrets.
6. Préserver les fonctionnalités existantes ; pas de suppression sans accord.
7. Interfaces modernes, fluides, soignées, cohérentes avec le thème choisi (Sobre/Classique).
8. Économie de tokens : lire uniquement les fichiers nécessaires (utiliser grep sur `index.html`, ne pas le lire en entier), pas d'analyses répétées, pas de sous-agents.
9. Demander l'autorisation avant chaque `git push`. Tout push déclenche un build.

## Mises à jour installables (priorité absolue)
Chaque APK doit s'installer par-dessus la version existante, sans désinstallation.
- Package : toujours `com.alexandre.focus` (`npx cap init Focus com.alexandre.focus`).
- Signature : toujours `debug.keystore` (racine), alias `androiddebugkey`, mots de passe `android`, injecté dans `signingConfigs.debug`. SHA-256 attendu : `16:5C:87:DA:86:52:94:C6:27:3F:11:CC:C7:38:22:EE:59:CC:84:2F:11:F6:B2:90:B6:C5:9A:5C:DF:40:55:E0`.
- Garder le build `assembleDebug` signé par ce keystore. Ne pas passer en `release`, ne pas utiliser un autre keystore, ni la signature générée par défaut sur le runner.
- `versionCode` strictement croissant (jamais inférieur ni égal au précédent), `versionName` mis à jour, définis dans le workflow.
- Ne jamais supprimer `debug.keystore` du dépôt, ne pas le régénérer, ne pas le renommer.
- Ne pas relever `minSdkVersion` sans accord (l'APK refuserait de s'installer sur le téléphone actuel).
- Ajouter dans le workflow une vérification `apksigner verify --print-certs` qui fait échouer le build si l'empreinte SHA-256 diffère.
- En cas de doute sur la signature ou le package : s'arrêter et demander.

## Architecture
- `index.html` : toute l'app (vanilla JS, ~4600 lignes), embarquée par Capacitor.
- Java natif (package `com.alexandre.focus`) : `MainActivity`, `StepCounterPlugin` (pas, Google Fitness), `MidnightReceiver` (minuit/boot), `FocusWidget` (+ `$Small`, `$Wide`, `$Large`), `WidgetConfigActivity`, `WidgetTicker`, `RestTimer`.
- `widget-res.zip` : res widgets, sons de repos, polices. `icones-focus.zip`, `icones-android.zip` : icônes.
- Fonctions : muscu (programmes, routines, séances, historique, stats, records, minuteur), podomètre + badges, nutrition (repas, recettes, scan code-barres ML Kit), poids, widgets, rappels, sauvegarde auto hebdo (Filesystem → Documents/Focus).
- Non utilisés : `build_1.yml`, `Focus-APK.zip`.

## Build (GitHub Actions)
- Déclencheur : chaque push (toutes branches) + manuel.
- Le projet Android est régénéré à chaque build : `cap init` → `cap add android` → copie des .java → manifest patché (sed/python) → `assembleDebug`.
- Toute modif Android (permissions, receivers, gradle, version) se fait dans le workflow, pas dans un dossier `android/`.
- Sortie : artifact `Focus-APK` + release `apk` (`Focus.apk`) remplacée à chaque build.
- `versionCode` n'est pas encore défini (vaut 1) : à ajouter dans le workflow.

## Données (ne pas casser)
- Web : `localStorage['focus.v1']` = objet JSON `S`. Ne jamais changer la clé. Ajouter des champs avec valeurs par défaut ; ne jamais renommer/supprimer sans migration.
- Ne pas changer `androidScheme`/`hostname` Capacitor (origine WebView = données localStorage).
- Natif : SharedPreferences `focus_widget` et `focus_steps`, noms des classes widget/receiver : ne pas renommer.

## Points d'attention
- Dépendances Capacitor non figées (`npm i` sans version) : une version majeure peut casser le build.
- Le keystore est déjà dans le dépôt (mots de passe `android`) : acceptable seulement si le dépôt est privé.
