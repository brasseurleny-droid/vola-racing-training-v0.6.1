# Vola Racing – Training (mode solo, v0.1.0)

Application Android de chronométrage d'entraînement (VTT, ski) : parcours A→B sur carte,
chrono automatique par franchissement de portes, enregistrement GPS + IMU + baromètre,
comparaison de passages, export CSV/GPX. Tout reste sur le téléphone.

## Nouveautés 0.6 (données propres, analyse intelligente, entre amis)
- **Filtrage des données** (courbes calculées uniquement, les CSV bruts ne sont jamais modifiés) : points GPS imprécis et
  sauts de position rejetés, pics de vitesse et d'altitude lissés, altitude issue du baromètre quand il est disponible,
  pente, accélérations latérale et longitudinale bornées (plus de pente à -120 % ni de -45 m/s² à l'arrêt). Une icône du
  laboratoire bascule entre données filtrées et brutes ; le détail du filtrage s'affiche sous les statistiques.
- **Analyse intelligente locale** : constats chiffrés (freinages, virages rapides, vitesse maxi, régularité entre runs,
  tendance de fatigue) et cause probable de chaque écart de temps (vitesse d'entrée, vitesse minimale, relance). Calculée sur le
  téléphone par des règles et des statistiques, sans réseau. Le bouton « Copier le résumé pour une IA » prépare un texte
  à coller dans l'assistant de votre choix : rien n'est envoyé par l'application.
- **Entre amis** (accueil > Entre amis) : pseudo, groupes, parcours communs, envoi d'un parcours (.volacourse) ou d'une session
  (.volasession) par la feuille de partage, import d'un paquet reçu, classement du groupe par parcours, comparaison des runs
  des membres dans le laboratoire (« + Tracé » filtre Tous / Moi / Amis). Sans serveur ; consentement avant tout envoi.
- **Écrans compacts** : bandeau réduit (encore plus en paysage, masqué en plein écran du laboratoire), puces de tracés plus petites.
- **Éditeur de parcours** : barre d'icônes (colonne à droite en paysage, ligne en bas en portrait), nom modifiable en touchant le
  titre (le clavier ne masque plus la carte), réglage fin dans une petite palette, appui long sur une icône = son rôle.
- **Habillage outdoor** : bandeau illustré (montagnes, neige, sapins, piste orange) sur l'accueil, icône d'application.
- Base de données v4 (migration automatique, données conservées).

### Pas encore fait
Mode Ghost (écarté : priorité aux amis), cardio Bluetooth, annotations, export des voies, flou des 100 premiers/derniers
mètres avant partage, synchronisation par serveur (voir ROADMAP.md).

## Nouveautés 0.5 (livraison 1 : analyse ergonomique, sessions libres fiables)
- Laboratoire : axe X gradué (valeurs, unité, titre) et graduations Y ; pastille de valeur du curseur sur l'axe ;
  panneau miniature des valeurs dans un coin (il change de coin tout seul, un appui le replie) ; barre d'outils en
  icônes miniatures sur le côté du graphique (appui long = libellé) ; carte et graphique visibles ensemble
  (portrait : séparateur glissable ; paysage : côte à côte) ; plein écran carte ou graphique ; l'état est conservé.
- Carte : trace colorée rouge -> vert selon la voie (vitesse, accélérations, pente, G, altitude, écarts), légende
  (toucher la légende pour inverser), liseré clair, surbrillance de la portion zoomée sur le graphique.
- Sessions libres : toute session de plus de 5 s donne au moins un run ; une vitesse GPS absente est déduite des
  positions ; récupération au démarrage des sessions orphelines (service tué, plantage) ; `session.json` écrit à la création.
- Découpage automatique des descentes à l'arrêt d'une session libre (runs « auto ») et écran « Découper en descentes »
  (bornes déplaçables au glisser, type, scinder, fusionner, validation). Seuils = hypothèses à valider (TODO).
- Parcours : départ, étapes et arrivée se déplacent en glissant leur pastille (D, 1, A) ; carré blanc = largeur des
  portes ; défaire / refaire.
- Pas de nouvelle migration de base (version 3). Workflow toujours manuel.

### Pas encore fait (livraison 2)
Groupes et sessions entre amis (paquets d'échange, import/export, runs des membres dans le laboratoire).
Reste aussi : plage de couleur commune entre runs, annotations, export des voies, Ghost, cardio Bluetooth.

## Nouveautés 0.4 (laboratoire d'analyse)
- Navigation à 4 onglets : Accueil, Enregistrer, Analyser, Parcours.
- Hiérarchie Session > Runs ; les parcours (pistes) ont leur propre écran avec progression et classement.
- Session libre (sans parcours) : enregistrement complet, runs analysables après coup.
- Laboratoire : un run seul ou jusqu'à 6 superposés ; graphique multi-voies (vitesse, altitude, pente, accélérations,
  force G, gyroscope, pression, précision GPS, satellites, écart de temps et de vitesse vs référence), voies empilées
  ou superposées sur 3 axes Y, axe X distance ou temps, curseur + table de valeurs, repère et mesure d'intervalle,
  zoom, statistiques, rapport automatique, synchronisation avec la carte.
- Réglages : un seul profil de détection avec préréglages VTT / Ski / Autre.
- Départ / Arrivée (au lieu de A / B), marqueurs plus petits.
- Base de données v3 (migration automatique, données conservées).
- Workflow GitHub Actions lancé À LA MAIN uniquement, un seul appel Gradle (tests + APK).

### Pas encore fait (prochaine livraison)
Déplacer les portes par glisser, détection automatique des descentes, annotations sur le graphique, export des voies
(CSV/image), mode paysage plein écran, fusion/scission de runs, mode Ghost, fréquence cardiaque Bluetooth.

## Nouveautés 0.3
Carte plein écran avec commandes transparentes, mode satellite, points intermédiaires (secteurs),
runs sauvegardés même sans franchir les portes (marqués « run libre »), comparaison alignée par position,
miniatures des tracés. La base de données passe en version 2 (migration automatique, vos données sont conservées).
Voir ROADMAP.md pour la suite.

## Compiler l'APK (sans rien installer) : GitHub Actions
1. Créez un dépôt GitHub, puis « Add file > Upload files » et déposez TOUT le contenu de ce dossier
   (y compris le dossier caché `.github`). Si le glisser-déposer ignore `.github`, créez le fichier
   `.github/workflows/build.yml` à la main (« Create new file ») et collez-y son contenu.
2. Onglet **Actions** > « Build APK » > **Run workflow** (se lance aussi à chaque envoi).
3. Au bout de ~5 min, ouvrez l'exécution et téléchargez l'artefact **app-debug** (ZIP contenant `app-debug.apk`).
4. Si l'exécution est rouge : ouvrez l'étape en échec et copiez l'erreur.

## Compiler en local
Android Studio (Koala ou plus récent), JDK 17 : ouvrir le dossier, laisser Gradle synchroniser.
Le wrapper `gradlew` n'est pas fourni : `gradle wrapper --gradle-version 8.9` (ou Android Studio le crée).
Ensuite `./gradlew test` puis `./gradlew assembleDebug`.

## Installer l'APK
Copiez `app-debug.apk` sur le téléphone, ouvrez-le, autorisez l'installation depuis cette source.
Sur Android 14, désactivez au besoin Play Protect pour les APK de test.

## Utilisation
1. Accueil : choisir VTT ou Ski, « Nouveau parcours ».
2. Parcours : toucher la carte pour poser A puis B (retoucher pour déplacer), régler la largeur de porte, enregistrer.
3. « Démarrer » : accorder la localisation précise. Le chrono part au franchissement de A, s'arrête à B
   (sens A→B uniquement). Voix et vibration au départ et à l'arrivée. « Marquer » pose un repère.
4. Comparer en 3 touchers : Accueil > Passages (1) > passage (2) > autre passage (3).
5. Export : bouton « Exporter » (Téléchargements/VolaRacing) ou « Partager » (ZIP CSV + GPX).

Fichiers bruts : `Android/data/fr.volaracing.training/files/sessions/<id>/` (gps.csv, imu.csv, marks.csv).
`imu.csv` : type `acc` (m/s², gravité incluse), `gyr` (rad/s), `baro` (hPa dans la colonne x).

## Précision
L'instant de franchissement est interpolé linéairement entre deux fixes GPS (jamais le point le plus proche).
Les tests simulent 100 km/h à 10 Hz, 1 Hz et 0,5 Hz : écart < 0,05 s sur trajectoire rectiligne.
Sur trajectoire courbe ou GPS bruité, l'erreur réelle dépend de la qualité du signal (typiquement 1 Hz sur téléphone).

## Limites connues
- Non compilé ni testé par l'auteur (pas de réseau ni de compilateur dans son environnement) : la première exécution
  GitHub Actions est le premier vrai test. Le test « 30 minutes écran verrouillé » est à faire sur téléphone.
- Version MapLibre `11.0.1` à vérifier à la compilation ; si elle n'est pas résolue ou si l'API diffère, ajuster `app/build.gradle.kts`.
- Autonomie : désactiver l'optimisation de batterie pour l'app si l'enregistrement est coupé (constructeurs agressifs).
- Pas de carte hors ligne (bouton « bientôt »). Pas de position de l'utilisateur affichée sur la carte (TODO).
- Écart cumulé : aligné par distance parcourue sur chaque trace ; approximatif si les trajectoires diffèrent.
- Police condensée : police système en attendant la charte Vola (TODO dans `Theme.kt`). Icône : icône système provisoire.
- Un seul ViewModel pour toute l'app (suffisant pour le MVP).
- Vitesse GPS : si le fournisseur ne la donne pas, elle vaut 0 (TODO : la déduire des positions).

## Décisions à prendre
1. Tuiles OSM : `tile.openstreetmap.org` est toléré pour un APK de test perso, pas pour une diffusion. Choisir un fournisseur ou un serveur de tuiles.
2. Précision GPS insuffisante : le passage est conservé et marqué « qualité faible » (au lieu d'être ignoré). À confirmer.
3. Profils par défaut (hypothèses) : VTT porte 20 m / 8 km/h ; Ski porte 30 m / 15 km/h ; fenêtre 1 s ; seuil 25 m.
4. Force G : accélération brute avec gravité (1 G au repos). Alternative : accélération linéaire filtrée.
5. Charte Vola : couleurs, police, icône.
6. Redémarrage du chrono si on repasse A pendant une course : actuellement le chrono repart de zéro à ce nouveau franchissement. À valider.
