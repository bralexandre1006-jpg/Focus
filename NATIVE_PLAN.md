# Réécriture native de Focus (Kotlin + Jetpack Compose)

À lire en premier pour reprendre le chantier. Mettre à jour la section **Avancement** à chaque étape.

## Règles du chantier
- Code natif dans `native/` (projet Gradle autonome), compilé par `.github/workflows/build-native.yml`.
- Pendant le chantier, l'APK natif s'installe **à côté** de l'app actuelle (`applicationIdSuffix ".native"`, nom « Focus Native ») et se télécharge via la release `native-apk`. La release `apk` (app actuelle) n'est jamais touchée.
- Bascule finale seulement quand toutes les fonctions ci-dessous sont faites et validées par l'utilisateur : retirer le suffixe, même `applicationId` (`com.alexandre.focus`), même `debug.keystore`, `versionCode` supérieur à celui de l'app web, puis faire publier la release `apk` par le build natif.
- Garder exactement les mêmes fonctionnalités, la police Jakarta en gras, l'accueil tenu sur un écran sans défiler, le style actuel (cartes teintées, couleurs).

## Performance (règle)
- Résultats calculés (stats, badges, série, historique) gardés en cache dans `FocusStore.cached()` tant que `rev` (version des données) ne change pas.
- Calculs lourds hors du fil d'affichage (`produceState` + `Dispatchers.Default`) : une transition ou un tap ne doit jamais attendre un calcul.
- Toute liste longue (historique, séances, aliments, recettes) en `LazyColumn` : seuls les éléments visibles sont lus et dessinés. Pas de calcul sur tout l'historique au rendu : agréger à la demande, mettre en cache si besoin.

## Données (aucune perte)
- Source de vérité : l'objet JSON de l'app web (`localStorage['focus.v1']`), gardé **brut** dans `FocusStore` (org.json) pour ne perdre aucun champ inconnu.
- Pont déjà en place dans `index.html` : à chaque sauvegarde, l'app web écrit `filesDir/focus-data.json`. Après la bascule (même package), l'app native lit ce fichier au premier lancement.
- Pendant le chantier (deux apps séparées) : import manuel du JSON exporté (Réglages › Données › Exporter) depuis l'écran de bienvenue natif.
- SharedPreferences natives existantes à conserver telles quelles : `focus_widget`, `focus_steps`.

## Inventaire des fonctions à porter
**Accueil** : en-tête (logo, flamme de série + gel), carte Pas (anneau, kcal brûlées, km, état capteur, boutons badges et aide), carte Programme (si actif), carte Poids (valeur, objectif %, barre, écart, saisie ✓ / ✕), carte Séance (jour de séance / repos / validée / manquée / jour off, « Lancer ma séance », séance en cours), carte Nutrition (kcal / objectif, bonus pas, macros, liste des repas par créneau dépliable, ajout).
**Série** : jour compté si info notée ou objectif de pas atteint ; gel d'un jour oublié contre objectif + 2 500 pas (proposition une fois, `frz` sur le jour).
**Repas** : ajout par recette ou ajout rapide (100 g / repas entier), créneaux (petit-déj, midi, soir, collations), recettes (création, ingrédients, portions, icône), aliments mémorisés, scan code-barres (ML Kit) + base d'aliments intégrée + produits, page Recettes en cartes pleine hauteur.
**Stats** : semaine / mois / année, pas (moyennes jour / semaine / mois, objectif atteint), calories, poids (moyenne 7 j, date estimée), bilans semaine et mois en mode histoire + image partageable.
**Historique** : par semaine, menu du jour (jour off par catégorie, modifier le jour, supprimer les stats), pas non modifiables.
**Muscu** (barre secondaire) : routines (création, exercices, séries, répétitions, repos, réglage machine, import / export), séance en cours (séries, poids, reps, records, minuteur de repos natif + sons, notes), historique des séances par mois, statistiques (volume / semaine, séries / muscle, progression, records), catégories perso.
**Badges** : au total, en un jour (10k → 150k), en une semaine, en un mois, en un an, défi 31 000 pas.
**Réglages** : profil et objectifs, rythme des séances (jours fixes / 1 sur 2), programme (import JSON), rappels, apparence (couleur, style d'icônes IconPark / Trait / Emoji, couleurs des cartes, mode performance), sons et vibrations, fonctionnalités, données (balance Health Connect, podomètre, sauvegarde hebdo, export / import, tout effacer), nouveautés.
**Natif existant à réutiliser** : `StepCounterPlugin` (capteur + Google Fitness local), `MidnightReceiver`, `WidgetTicker`, `FocusWidget` + `WidgetConfigActivity` (4 tailles, 50 styles), `RestTimer`, `HealthPlugin` (Health Connect).
**Divers** : onboarding, écran d'ouverture (logo qui s'assemble), nouveautés, notifications locales (rappels, bilan du dimanche), retour Android.

## Avancement
- [x] Projet `native/` (Compose, AGP 8.7.3, Kotlin 2.0.21), workflow séparé, signature commune.
- [x] `FocusStore` : lecture / écriture du JSON brut, poids, macros, série.
- [x] Pont web → `focus-data.json`.
- [x] Accueil v0 : en-tête + série, cartes Pas, Poids (saisie), Nutrition. Import du JSON.
- [x] Ouverture native (logo qui s'assemble, rebond, zoom), barre d'onglets avec pastille (200 ms), navigation 5 onglets.
- [x] Accueil v1 : carte Séance (faite / ratée / repos / off, jours fixes), repas dépliables dans Nutrition.
- [x] Premières versions lecture seule : Repas (recettes en cartes), Muscu (routines + dernières séances), Stats (pas 7 j + poids), Historique (jour par jour).
- [x] Accueil v2 : carte Programme, « Lancer ma séance » (ouvre Muscu), gel de série (proposition + progression), badges (5 groupes, mêmes paliers).
- [ ] Accueil : aide « comment c'est calculé », décalages de séance et rythme 1 sur 2, ajout de repas depuis l'accueil.
- [ ] Réglages natifs.
- [ ] Podomètre natif branché (StepCounter) et widgets mis à jour depuis le natif.
- [ ] Repas et recettes, scan code-barres.
- [ ] Muscu complète.
- [ ] Stats, historique, bilans, badges.
- [ ] Réglages, rappels, notifications, Health Connect.
- [ ] Onboarding, ouverture, nouveautés.
- [ ] Validation utilisateur puis bascule.
