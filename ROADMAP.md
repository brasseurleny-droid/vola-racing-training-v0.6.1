# Vola Racing Training : feuille de route (tri du « prompt idéal »)

Principe : on construit par couches, et chaque couche doit être utile seule. On ne vend pas d'IA avant
d'avoir des mesures fiables : un conseil IA bâti sur un chrono faux est pire que pas de conseil.

## Déjà en place (v0.6)
Filtrage des données, analyse intelligente locale (règles et statistiques), échanges entre amis par fichiers, éditeur
compact, habillage outdoor. Prochaine étape IA : coach en langage naturel branché sur ces constats (clé personnelle,
consentement explicite), puis synchronisation serveur des groupes.

## Avant (v0.5, solo, local)
Laboratoire multi-voies avec axes gradués, carte colorée par voie, découpage automatique des descentes, sessions libres
récupérables, portes déplaçables au glisser (voir README). Les lignes ci-dessous détaillent l'historique.

## Historique (v0.3)
Enregistrement GPS + IMU + baromètre, chrono par portes (temps interpolé), points intermédiaires (secteurs),
runs libres sauvegardés sans portes, comparaison de deux passages alignés par position, écart cumulé,
miniatures, carte plan/satellite, export CSV/GPX, écran d'effort.

## MVP+ (prochaines versions, 100 % sur le téléphone, sans serveur)
1. Détection automatique des descentes (par altitude + vitesse) pour découper un enregistrement sans portes.
2. Mode Ghost : choisir un passage de référence, afficher l'avance/retard en direct (voix + écran).
3. Métriques de run : D+, D-, pente moyenne/max, vitesse min/moy/max, freinages (décélérations fortes),
   relances, régularité de vitesse. Tout se calcule à partir des CSV déjà enregistrés.
4. Fréquence cardiaque : Bluetooth LE standard (profil HR, sangles et nombreuses montres en diffusion).
5. Scores 0-100 (fluidité, régularité, freinage) : formules simples et expliquées, pas de boîte noire.
6. Rapport de passage automatique en texte (règles + gabarits) : « vous perdez 1,2 s entre 200 et 400 m,
   freinage plus long de 0,8 s ».
7. Carte hors ligne (téléchargement de zone), position affichée sur la carte de course.

## V2 (avec compte et serveur)
- Synchronisation cloud, historique multi-appareils, partage de parcours entre athlètes.
- Mode Coach : plusieurs athlètes, tableau de comparaison, charge d'entraînement.
- Coach IA conversationnel : un modèle de langage reçoit des métriques déjà calculées (jamais les points GPS
  bruts) et répond à « pourquoi plus lent aujourd'hui ? ». L'analyse reste déterministe, l'IA l'explique.
- Montres : Health Connect (Android), HealthKit (iOS), API Garmin (accès partenaire sur demande).
- Version iOS.

## V3 (ambitieux, à valider par le marché)
- Replay 3D (terrain, deux runs superposés), détection de sauts et de virages techniques (IMU),
  prévision de performance, détection de fatigue et de stagnation, alertes de blessure.

## Choix techniques recommandés
- Mobile : Android natif Kotlin aujourd'hui (accès GPS/capteurs de précision, service de premier plan).
  iOS plus tard : SwiftUI natif ou Kotlin Multiplatform pour partager la logique de calcul (le dossier
  `core` est déjà du Kotlin pur, donc partageable).
- Cartes : MapLibre. Tuiles : un fournisseur avec contrat (MapTiler, Stadia, ou serveur propre) avant diffusion.
  Relief/3D : tuiles terrain MapLibre ou Cesium.
- Backend V2 : Postgres + PostGIS (traces, parcours, recherche de pistes proches), stockage objet pour les CSV
  bruts, API simple (FastAPI ou Supabase). Calculs lourds dans des workers Python (NumPy/SciPy).
- IA : d'abord statistiques et règles (explicables), ensuite modèle de langage via API pour la rédaction et le
  chat. Entraîner des modèles maison seulement quand il y aura des milliers de runs étiquetés.
- Données : local d'abord, consentement explicite pour toute synchro, export et suppression (RGPD).

## Différenciation face à Strava / Garmin
- Précision de chronométrage de niveau compétition (interpolation aux portes), pas un simple « segment ».
- Comparaison run contre run avec explication (où, pourquoi), pas seulement un classement.
- Spécialisation descente VTT et ski, y compris sans réseau.
- Pont avec l'écosystème Vola (résultats live, Vola Vision, comparaison vidéo) : entraînement et compétition
  dans la même app. C'est l'avantage que Strava et Garmin n'ont pas.

## Modèle économique (hypothèse)
Gratuit : enregistrement, chrono, comparaison de deux runs. Premium (abonnement) : historique illimité, Ghost,
coach IA, replay. B2B : clubs, équipes, fédérations (mode Coach multi-athlètes, licence par structure).

## Risques à surveiller
- GPS de téléphone à 1 Hz : limite physique de précision (voir README). Un récepteur GNSS externe est une option.
- Autonomie batterie et arrêts du service par certains constructeurs.
- Accès aux API des montres (partenariats), coût des tuiles et de l'IA, sécurité d'usage (pas de manipulation
  du téléphone en mouvement).
