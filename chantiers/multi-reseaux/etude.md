# Étude : ouvrir Tracéo Live à d'autres villes

Étude du 11 novembre 2026. Elle porte sur l'appli Android Tracéo Live (ce dépôt) et sur le serveur
Tracéo (gotraceo.fr). Rien n'a été modifié dans le code : c'est une étude, avec des mesures faites
en direct sur les flux publics.

## 1. En bref

- **C'est faisable, et pour beaucoup de villes c'est simple.** Le catalogue national
  (transport.data.gouv.fr) recense 198 réseaux français qui publient leurs prochains passages au
  format GTFS-RT. J'en ai sondé 27 en direct : tous donnent des heures de passage absolues et des
  identifiants qui se rattachent aux horaires publiés.
- **Le format GTFS-RT ressemble à ce que Tracéo fait déjà.** C'est un flux global du réseau, relevé
  à intervalle fixe, comme le SIRI ET de PRIM. Le modèle actuel (un relevé global, des passages
  gardés en mémoire, une route légère pour le widget) se transpose tel quel.
- **Recommandation : passer par un cache sur Tracéo, pas par le téléphone en direct.** Un flux
  pèse de 3 Ko (Nîmes) à 965 Ko (Bordeaux) à chaque actualisation, la plupart des gros ne sont pas
  compressés, et les adresses changent alors que les APK installés ne se mettent pas à jour seuls.
- **Les deux plus grandes villes après Paris sont les plus difficiles.** Lyon exige un compte,
  Marseille ne publie que du SIRI SOAP. Une première version couvrirait Bordeaux, Nice,
  Montpellier, Rouen, Dijon et une quinzaine d'autres, mais ni Lyon ni Marseille.
- **Le métro manque dans trois grands flux.** À Toulouse, Rennes et Lille, le flux GTFS-RT contient
  les bus et les trams mais aucune ligne de métro. Il faut une seconde source par ville.

Le classement en cinq groupes est au chapitre 5, le plan au chapitre 7, les recommandations au
chapitre 8.

### Décisions prises le 11 novembre 2026, après lecture

- Le chantier se fait sur la branche `multi-reseaux` et sort en version 3.0 de l'appli.
- Cache sur Tracéo (D1), par un service de collecte GTFS-RT pensé pour servir aussi le site plus
  tard.
- **Troyes (TCAT) est le réseau pilote** (D4), à la place de Bordeaux et Dijon proposés plus bas.
  Bordeaux reste le bon second réseau, pour éprouver un gros flux.
- La configuration commence par le choix du réseau, aidé par la position approximative sans la
  rendre obligatoire.
- Le format À proximité et le mode automatique doivent changer de réseau seuls quand l'usager
  change de ville.

Le plan du pilote et le contrat entre l'appli et Tracéo sont dans
[pilote-troyes.md](pilote-troyes.md) ; le prompt destiné au projet Tracéo dans
[prompt-traceo.md](prompt-traceo.md). Les chapitres qui suivent sont l'étude d'origine, non
réécrite.

## 2. Ce qui a été vérifié, et ce qui ne l'a pas été

### Vérifié

| Vérification | Résultat |
|---|---|
| Catalogue national, relevé le 11/11/2026 | 246 jeux avec du temps réel, dont 198 avec des passages GTFS-RT (« TripUpdates ») et 50 avec du SIRI ou SIRI Lite |
| Flux relayés par le proxy national, sans clé | 71 des 198 jeux GTFS-RT |
| Taille des réseaux GTFS-RT | 10 ont plus de 5 000 arrêts, 31 plus de 2 000, 69 plus de 1 000 |
| Licences des 246 jeux | Licence Ouverte : 168 ; ODbL : 75 ; Licence Mobilités : 2 (IDFM et Lyon) ; non précisée : 1 |
| 27 flux GTFS-RT sondés entre 18 h 57 et 19 h 04 | Tous lisibles ; `trip_id` et `stop_id` présents à 100 % ; heure absolue sur 96 à 100 % des passages ; relevé vieux de 1 à 106 secondes |
| Concordance avec les horaires théoriques (Toulouse, Bordeaux, Rennes, Lille, Tours, Nice, SNCF) | `trip_id` retrouvés à 99 ou 100 %, `stop_id` à 100 % |
| Décodage sans bibliothèque | Un décodeur de 120 lignes en Python pur lit un flux en 5 à 200 ms : aucune dépendance protobuf n'est nécessaire, côté serveur comme côté appli |
| Compilation d'un index d'arrêts par réseau | 0,6 à 2,7 secondes ; fichier de 36 à 135 Ko une fois compressé |

Le détail par réseau est en annexe A.

### Non vérifié

- **Une seule mesure, un soir de jour férié.** Les tailles en heure de pointe d'un jour ouvré seront
  plus élevées, sans doute de deux à trois fois. À remesurer avant tout dimensionnement.
- **Vingt des 27 flux n'ont pas été confrontés à leurs horaires théoriques**, seulement lus.
- **Les quelque 170 autres réseaux GTFS-RT du catalogue n'ont pas été sondés.** Je les classe
  d'après ce que le catalogue annonce.
- **Je n'ai pas mesuré la couverture** : la part des passages réellement prévus qui figurent dans le
  flux temps réel. Une course qui n'a pas encore démarré peut manquer.
- **Marseille** : seule la disponibilité du service a été testée (réponse 200 sans clé), pas une
  demande de passages.
- **Les conditions d'utilisation de chaque portail n'ont pas été lues.** Les licences citées sont
  celles que déclare le catalogue.

## 3. Les technologies rencontrées

| Technologie | Ce que c'est | Où on la trouve | Ce qu'elle demande |
|---|---|---|---|
| **GTFS-RT TripUpdates** | Un fichier binaire qui décrit toutes les courses en cours du réseau, avec l'heure prévue à chaque arrêt | 198 réseaux, dont Bordeaux, Toulouse, Lille, Nice, Nantes, Rennes, Montpellier, et la SNCF | Les horaires théoriques (GTFS) pour traduire les identifiants en noms de ligne, d'arrêt et de destination |
| **SIRI Lite** | Une API par arrêt ou par ligne, en JSON, la même norme que PRIM | Île-de-France, Lyon, Strasbourg, Bordeaux, Nantes, SNCF | Souvent un compte ou un jeton |
| **SIRI SOAP** | La même norme, en XML, avec une enveloppe à construire pour chaque demande | Marseille, Caen, Besançon, Pau, La Rochelle, Niort | Un client SOAP, et souvent une convention avec l'exploitant |
| **API propres au réseau** | Une API maison par arrêt | Rennes (ouverte), Grenoble (sous conditions), Toulouse et Besançon (à clé) | Un adaptateur par réseau |

Le GTFS-RT a deux limites à connaître. Il ne donne ni le nom de la ligne ni la destination : il
faut les lire dans les horaires théoriques. Et il ne dit pas si le véhicule est à quai ni sur
quelle voie : « À quai » et « Train et voie » restent propres à l'Île-de-France.

J'ai testé un raccourci, sans succès : déduire la destination du dernier arrêt listé dans le flux.
Cela ne tombe juste que pour 1 % des courses à Lille, 41 % à Rennes et 84 % à Toulouse. La table
des courses reste donc nécessaire.

## 4. Deux façons de servir une ville

### Voie A : le téléphone interroge le flux lui-même, sans cache Tracéo

Le téléphone télécharge le flux du réseau à chaque actualisation et y cherche son arrêt.

### Voie B : Tracéo relève le flux et le widget interroge Tracéo

Tracéo relève chaque flux une fois par intervalle, garde les passages par arrêt en mémoire et sert
au widget une réponse de la même forme qu'aujourd'hui.

### Comparaison

| Critère | Voie A, sans cache | Voie B, avec cache Tracéo |
|---|---|---|
| Données par actualisation | Le flux entier : 3 Ko à Nîmes, 139 Ko à Montpellier, 572 Ko à Toulouse, 965 Ko à Bordeaux | Environ 1 Ko, comme aujourd'hui |
| À une actualisation toutes les 2 min | Jusqu'à 29 Mo par heure d'écran allumé à Bordeaux | Négligeable |
| Adresse d'un flux qui change | Widgets cassés jusqu'à la mise à jour de l'appli | Corrigé sur le serveur, sans nouvelle version |
| Clés et comptes | Impossible : une clé dans un APK public s'extrait. Reste la clé personnelle de l'usager, comme pour PRIM | La clé reste sur le serveur |
| Sources bridées | Chaque téléphone se heurte à la limite (Nantes : 7 refus sur 10 essais) | Un seul client, qui sert son dernier relevé daté |
| Confidentialité | Le téléphone contacte le proxy national et les serveurs des exploitants : politique et fiche Play à revoir | Inchangée : le téléphone ne parle qu'à gotraceo.fr |
| Développement dans l'appli | Lourd : décodeur, table des courses, regroupement par arrêt, un adaptateur par technologie | Léger : le client Tracéo existant, avec un réseau en plus |
| Développement sur Tracéo | Aucun sur le serveur ; il faut quand même publier un catalogue et des index compilés, par exemple en fichiers statiques | Un service de collecte et quelques routes |
| Si Tracéo tombe | Les autres villes continuent | Tout s'arrête, comme pour l'Île-de-France sans clé |

La voie A n'est défendable que pour les petits réseaux, ceux dont le flux pèse moins de 50 Ko. Même
là, elle laisse le problème des adresses qui changent.

### Le socle commun, quel que soit le groupe

Ces prérequis valent pour toutes les villes. Les groupes du chapitre 5 n'indiquent que ce qui s'y
ajoute.

**Sur Tracéo (voie B)**

1. **Un service de collecte séparé du site.** Le serveur actuel est un seul processus Python ;
   décoder un flux de 1 Mo y disputerait le processeur aux requêtes du site pendant environ
   200 ms (mesuré sur ce poste). Un second processus, ou un second conteneur derrière le même
   nginx, isole le site francilien des pannes et de la charge des autres villes.
2. **Un registre des réseaux** : identifiant, nom, emprise géographique, adresse du GTFS, adresse
   du flux, licence, mention à afficher, cadence de relevé. Un fichier de configuration suffit.
3. **Un décodeur GTFS-RT en Python pur**, sans dépendance, donc sans nouvel ADR.
4. **Une compilation quotidienne des horaires théoriques** par réseau : lignes, arrêts regroupés
   par zone, table des courses (ligne, sens, destination), lignes desservant chaque arrêt. Les
   fichiers pèsent de 5 à 21 Mo et se compilent en moins de 3 secondes.
5. **Un rechargement quand les identifiants ne concordent plus.** Un réseau qui publie de nouveaux
   horaires change ses `trip_id` ; le taux de courses non retrouvées doit déclencher le
   rechargement.
6. **Des routes nouvelles, à côté des routes actuelles** : la liste des réseaux, les lignes d'un
   réseau, ses arrêts, ses passages, son index d'arrêts. Le contrat figé de
   `/api/widget/passages` (RG-44) ne bouge pas, et sa clé de cache nginx non plus.
7. **Le même habillage que la route du widget** : `fetchedAt`, `fresh`, `nextAt`, ETag, cache
   nginx, limite de débit par IP.
8. **Une collecte à la demande.** Un réseau n'est relevé que si un widget l'a demandé dans les dix
   dernières minutes. Relever en continu les 27 flux sondés coûterait environ 4 Mo par relevé,
   soit 5 à 6 Go par jour à un relevé par minute, et davantage en pointe.
9. **Une supervision par réseau** : âge du dernier relevé, taux de concordance, mise en sommeil
   d'un flux muet. Le registre de métriques existant s'y prête.
10. **Les mentions légales par réseau**, servies avec le catalogue, et une page des sources sur
    gotraceo.fr. La Licence Ouverte demande la source et la date ; l'ODbL demande en plus de
    partager la base dérivée aux mêmes conditions.

**Sur Tracéo Live**

1. **Un identifiant de réseau dans le modèle.** Aujourd'hui `line:IDFM:` et `stop_area:IDFM:` sont
   écrits en dur (`TraceoClient`, `NearbyLine`), et `Referential.bareLineId` ne garde que la fin
   de l'identifiant : la ligne « 1 » existe dans chaque ville. Les widgets déjà posés doivent
   rester valables, avec l'Île-de-France par défaut.
2. **Des modes par réseau.** `TransportMode` énumère Métro, RER, Train, Tramway, Bus, Câble. Il
   faut des modes génériques (bateau à Bordeaux, téléphérique à Toulouse) et réserver RER et
   Transilien à l'Île-de-France.
3. **Une pastille de ligne générique** : couleur et texte du réseau, noms courts jusqu'à cinq
   caractères (« C20 », « TE3 »). `BadgeRenderer` choisit aujourd'hui la forme selon les modes
   franciliens.
4. **Le choix de la source par réseau.** `DataSources.route` décide entre Tracéo et PRIM selon la
   présence d'une clé ; la clé PRIM ne doit compter que pour l'Île-de-France.
5. **Les favoris et les lieux mémorisés** rattachés à un réseau.
6. **Un index d'arrêts par réseau pour le mode automatique**, téléchargé à la demande. L'index
   actuel est construit à partir de quatre jeux de l'open data IDFM.
7. **Les mentions par réseau** : `data_mention` et `sources_not_affiliated` citent PRIM et Île-de-
   France Mobilités en dur.
8. **Les règles propres à l'Île-de-France à neutraliser ailleurs** : Noctilien (`isNightLine`),
   retrait des arrivées au terminus, quota PRIM.

### Impacts communs sur les écrans, les comportements et les parcours

**Écrans**

| Écran | Changement |
|---|---|
| Configuration d'un widget | Une étape « Réseau » avant le mode : cinq étapes au lieu de quatre. Elle est préremplie avec le dernier réseau choisi et se modifie depuis le fil d'Ariane. La liste des modes dépend du réseau. La ligne « Ajouter une clé API PRIM » n'apparaît que pour l'Île-de-France |
| Accueil | Le nom du réseau sur la carte de chaque widget ; la mention des données selon les réseaux utilisés |
| Widget | Mention de source par réseau à la place de « PRIM via Tracéo » ; pastille générique ; pas de « À quai » ni de voie hors Île-de-France |
| Galerie des formats | « Train et voie » reste réservé à l'Île-de-France avec clé |
| Réglages et clé | La clé PRIM est présentée comme propre à l'Île-de-France ; le choix d'une actualisation à 1 min dépend de la cadence du réseau |
| Sources et licences | Une entrée par réseau utilisé, alimentée par le catalogue |
| Lignes proches (mode automatique) | Le réseau est déduit de la position ; un premier passage dans une ville télécharge son index |
| Notification, tuile, Live Update | Libellés génériques (« Tram B » au lieu de « Métro 13 ») |
| Fiche Play Store, README, politique de confidentialité | « en Île-de-France » devient une liste de villes. En voie B la politique ne change pas sur le fond |

**Comportements**

- **Fraîcheur.** Les relevés sondés avaient de 1 à 106 secondes, quand le relevé francilien se
  renouvelle toutes les 2 minutes. Je n'ai pas mesuré la cadence de renouvellement de chaque
  flux. `nextAt` suffit déjà à caler le widget sur la cadence de chaque réseau.
- **Retards.** Toulouse, Bordeaux, Nantes et la SNCF donnent le retard. Rennes, Lille et Nice ne
  donnent que l'heure : le retard se calcule sur le serveur à partir de l'horaire théorique, ou
  n'est pas affiché.
- **Suppressions.** Le flux les signale : « Supprimé » fonctionne. Toulouse en annonçait 150 au
  moment de la sonde.
- **Ligne sans temps réel.** Elle doit être signalée dès la configuration, et non donner un widget
  vide.
- **Réveil d'un réseau.** Avec la collecte à la demande, le premier widget d'un réseau endormi
  attend le relevé suivant, une minute au plus.

**Parcours**

- **Premier lancement hors Île-de-France.** Aujourd'hui l'usager tombe sur les modes franciliens.
  Il choisirait d'abord son réseau, proposé par proximité s'il a accordé la position.
- **Voyageur.** En mode automatique, le widget change de réseau avec la ville.
- **Ville absente.** Un message clair, avec un moyen de la demander.
- **Usagers actuels.** Rien ne change pour eux : leurs widgets restent en place.

## 5. Classement des villes en cinq groupes

Du plus simple au plus difficile. Chaque groupe suppose le socle du chapitre 4.

| Groupe | Critère | Villes | Sans cache | Avec cache |
|---|---|---|---|---|
| **1. Flux ouvert et complet** | GTFS-RT sans clé, tous les modes du réseau | Bordeaux, Nice, Montpellier, Rouen, Toulon, Saint-Étienne, Tours, Clermont-Ferrand, Angers, Orléans, Dijon, Reims, Brest, Le Havre, Le Mans, Metz, Nancy, Mulhouse, Amiens, Nîmes, Lens | Possible pour les petits flux, déconseillé pour Bordeaux | Simple : une ligne de registre par réseau |
| **2. Flux ouvert, une pièce manque** | GTFS-RT sans clé, mais un mode absent ou un accès bridé | Toulouse, Rennes, Lille, Nantes, SNCF (TER, TGV, Intercités) | Partiel | Le socle, plus un complément par ville |
| **3. API à identifiants** | Compte ou jeton obligatoire | Lyon, Strasbourg, Grenoble, Besançon | Seulement avec la clé personnelle de l'usager | Une clé au nom de Tracéo et un adaptateur par ville |
| **4. SIRI SOAP et accès bridés** | Pas de GTFS-RT, demandes XML par arrêt | Marseille et Aix, Caen, La Rochelle, Niort, Pau | Non | Un client SOAP et un adaptateur par plateforme |
| **5. Pas de temps réel ouvert** | Horaires théoriques seuls | Limoges, et beaucoup de petits réseaux | Non | Hors périmètre |

### Groupe 1 : flux ouvert et complet

**Villes sondées.** Bordeaux (tram, bus, bateau), Nice (tram, bus), Montpellier, Rouen, Toulon,
Saint-Étienne, Tours, Clermont-Ferrand, Angers, Orléans, Dijon, Reims, Brest, Le Havre, Le Mans,
Metz, Nancy, Mulhouse, Amiens, Nîmes, Lens. La concordance avec les horaires et la présence de
tous les modes ont été vérifiées pour Bordeaux, Nice et Tours seulement. À ma connaissance, aucune
de ces villes n'a de métro automatique, le mode qui manque à Toulouse, Rennes et Lille. L'agrégat
régional de Normandie (660 Ko) a été sondé aussi ; il recoupe Rouen et Le Havre.

**Villes annoncées par le catalogue, à qualifier.** Perpignan, Valenciennes, Dunkerque, Avignon,
Cannes, Chambéry, Poitiers, Angoulême, Troyes, Lorient, Vannes, Quimper, Saint-Malo, Bourges,
Chartres, Blois, Arras, Calais, Valence, Bayonne, la Martinique, La Réunion, et une centaine de
réseaux plus petits.

**Difficulté : faible.** Une fois le socle en place, ajouter un réseau revient à écrire une entrée
du registre et à passer la sonde de qualification.

**Prérequis sur Tracéo.** Le socle, rien de plus. Deux points d'attention :

- Tours et la SNCF n'indiquent pas la ligne dans le flux : elle se lit dans la table des courses.
- Bordeaux, Le Mans, Perpignan et Bayonne placent dans l'adresse une clé publique, publiée au
  catalogue national. Elle va dans le registre, pas dans l'appli.

**Prérequis sur Tracéo Live.** Le socle, rien de plus.

**Impacts.** Ceux du chapitre 4. Les lignes de bus sans équipement embarqué n'ont pas de temps
réel : à signaler à la configuration.

**Sans cache.** Jouable pour les petits flux, de Nîmes (3 Ko transférés) à Nancy (23 Ko).
Déconseillé au-delà de 100 Ko : Montpellier, Toulon, Angers, Orléans, Nice, Bordeaux.

### Groupe 2 : flux ouvert, une pièce manque

| Ville | Ce qui marche | Ce qui manque | Complément possible |
|---|---|---|---|
| **Toulouse** | Bus, tram, téléphérique (572 Ko) | Les métros A et B | L'API Tisséo, qui répond 401 sans clé : une clé à demander, et à vérifier qu'elle donne bien le métro |
| **Rennes** | Bus (94 Ko) | Les métros a et b | L'API ouverte du réseau STAR donne les deux prochains passages du métro, sans clé : vérifié |
| **Lille** | Bus et tram (48 Ko) | Les métros M1 et M2 | Le flux SIRI relayé par le proxy national contient le métro ligne 1 (la ligne 2 y figurait ce soir-là sous forme de bus relais), mais pèse 6,2 Mo de XML pour tout le réseau |
| **Nantes** | Tram, busway et bus, flux complet (488 Ko) | Un accès fiable : 7 refus sur 10 essais, « 1 requête par 30 secondes », limite partagée entre tous les réutilisateurs du proxy | Demander un accès propre à l'exploitant |
| **SNCF** | TER, TGV, Intercités, toute la France (898 Ko, 164 Ko transférés) | La voie ; et un parcours pensé pour les gares | Rien à ajouter côté données |

**Difficulté : moyenne.** Le flux principal se branche comme au groupe 1. Le coût est dans le
complément, différent pour chaque ville.

**Prérequis sur Tracéo, en plus du socle.**

- Un second adaptateur par ville pour le métro, qui alimente les mêmes passages en mémoire.
- Pour Lille, un lecteur SIRI en XML. Celui du relevé francilien peut servir de base.
- Pour Toulouse et Nantes, une clé ou un accès obtenus auprès de l'exploitant : c'est une démarche
  que toi seul peux faire.
- Pour la SNCF, la gestion des trains ajoutés (142 au moment de la sonde) et supprimés (16), et un
  identifiant de gare stable.

**Prérequis sur Tracéo Live, en plus du socle.**

- Une ligne sans temps réel doit pouvoir s'afficher comme telle : grisée, avec « Temps réel
  indisponible sur cette ligne ».
- Pour la SNCF : une recherche par gare plutôt que par ligne, le numéro du train affiché comme le
  code mission des Transilien, et un horizon plus long (les trains sont annoncés 12 heures à
  l'avance).

**Impacts.**

- **Tant que le complément n'est pas fait**, un usager de Toulouse, Rennes ou Lille ne trouve pas
  son métro. C'est le mode le plus utilisé de ces réseaux : je déconseille d'annoncer ces villes
  avant.
- **Nantes** : avec le cache, le widget affiche un relevé parfois vieux de deux ou trois minutes,
  daté comme tel. Sans cache, il serait refusé la plupart du temps.
- **SNCF** : un nouveau parcours, « Train » puis gare puis direction, qui recoupe l'Île-de-France.
  Le jeu est annoncé comme TGV, Intercités et TER ; je n'ai pas vérifié que les Transilien en sont
  absents, ce qu'il faut contrôler pour éviter un doublon avec PRIM.

**Sans cache.** Rennes est faisable, par l'API par arrêt du réseau, ouverte, sous réserve du quota
anonyme du portail, que je n'ai pas vérifié. Lille l'est pour le bus et le tram seulement.
Toulouse et la SNCF sont trop lourds. Nantes est exclu.

### Groupe 3 : API à identifiants

| Ville | Constat | Technologie |
|---|---|---|
| **Lyon** | 401 sans compte sur data.grandlyon.com ; Licence Mobilités, comme IDFM | SIRI Lite : la norme de PRIM |
| **Strasbourg** | 401 sans jeton | SIRI Lite : passages par arrêt et flux global |
| **Grenoble** | Le flux GTFS-RT du catalogue répond 403 ; l'API du réseau exige un en-tête d'origine et un contact pour un usage massif | API propre au réseau |
| **Besançon** | Clé obligatoire | API propre et SIRI SOAP |

**Difficulté : moyenne à forte.** La technique est connue, puisque c'est celle de l'Île-de-France.
Le coût est administratif (un compte, des conditions à accepter, un quota à respecter) et il y a
un adaptateur à écrire par ville.

**Prérequis sur Tracéo, en plus du socle.**

- Un compte ou un jeton par réseau, au nom de Tracéo, gardé sur le serveur, hors du dépôt, comme la clé PRIM.
- Un gouverneur de quota par réseau, sur le modèle de celui du relevé francilien.
- Pour Lyon, une adaptation du collecteur SIRI existant : même norme, autre référentiel.
- La lecture des conditions de chaque portail avant de redistribuer les données par le cache.

**Prérequis sur Tracéo Live, en plus du socle.** Rien si tout passe par le cache.

**Impacts.** Ceux du chapitre 4, plus une fraîcheur qui dépend du quota accordé.

**Sans cache.** Possible sur le modèle actuel de PRIM : l'usager crée son compte et saisit sa clé.
L'analyseur SIRI Lite de l'appli se réutilise pour Strasbourg et Lyon. L'impact est alors lourd
dans l'appli : un écran de clé par réseau, un compteur de quota par réseau, un référentiel par
réseau. Et peu d'usagers iront créer un compte.

### Groupe 4 : SIRI SOAP et accès bridés

| Ville | Constat |
|---|---|
| **Marseille, Aix et la métropole** | 30 points d'accès SIRI SOAP, un par réseau et par exploitant. Le GTFS-RT ne contient que les alertes. Le service répond sans clé |
| **Caen, La Rochelle, Niort, Pays de la Loire, Dordogne, Libourne** | SIRI SOAP sur une plateforme commune. Sur Caen, elle a répondu « 1 requête par 10 minutes » en accès anonyme ; les autres n'ont pas été sondés |
| **Pau, Montbéliard, Saint-Brieuc** | SIRI SOAP, non sondés |

**Difficulté : forte.** Pas de flux global à relever : il faut interroger arrêt par arrêt, en XML,
avec une enveloppe à construire, ou s'abonner à des notifications.

**Prérequis sur Tracéo, en plus du socle.**

- Un client SIRI SOAP : enveloppe, demande de passages par arrêt, lecture de la réponse.
- Un cache par arrêt avec une durée de vie courte, à la place du relevé global. Le nombre de
  widgets pèse alors sur le nombre d'appels : c'est l'inverse du principe de RG-44, et il faut un
  budget par réseau.
- Pour Marseille, la carte des 30 points d'accès vers les lignes.
- Pour la plateforme commune, une convention ou une clé : à 1 requête par 10 minutes, rien n'est
  possible.

**Prérequis sur Tracéo Live, en plus du socle.** Rien.

**Impacts.** Une fraîcheur moins bonne et moins régulière, et un risque de quota que les autres
groupes n'ont pas.

**Sans cache.** Non.

### Groupe 5 : pas de temps réel ouvert

Les réseaux qui ne publient que leurs horaires théoriques. Les afficher demanderait un moteur
d'horaires complet (calendriers, jours fériés, exceptions) et une mention « horaire théorique » sur
chaque passage. Je le laisse hors périmètre.

### Hors classement : l'étranger

Le GTFS-RT est la norme hors de France. Le socle du groupe 1 s'y appliquerait, mais l'appli est en
français seulement et plusieurs règles supposent l'heure de Paris. À ne regarder qu'après.

## 6. Ce que cela pèse sur le serveur

Mesures faites sur ce poste, en Python pur, un soir de jour férié.

| Réseau | Fichier d'horaires | Lignes de `stop_times` | Compilation de l'index | Index compressé | Table des courses compressée | Décodage du flux |
|---|---|---|---|---|---|---|
| Bordeaux | 21,3 Mo | 2 127 677 | 2,7 s | 75 Ko | 223 Ko | 202 ms |
| Toulouse | 11,1 Mo | 778 152 | 0,9 s | 81 Ko | 124 Ko | 79 ms |
| Rennes | 11,0 Mo | 1 328 034 | 1,5 s | 36 Ko | 207 Ko | 18 ms |
| Lille | 9,5 Mo | 1 210 954 | 1,3 s | 56 Ko | 198 Ko | 10 ms |
| Nice | 7,3 Mo | 467 819 | 0,6 s | 55 Ko | 181 Ko | 35 ms |
| SNCF | 5,5 Mo | 510 514 | 0,9 s | 135 Ko | 515 Ko | 79 ms |

Le processeur n'est pas la contrainte : 27 flux décodés chaque minute occupent moins de 5 % d'un
cœur. La contrainte est le volume téléchargé, d'où la collecte à la demande. Le serveur actuel
devrait suffire pour une vingtaine de réseaux : c'est une estimation, la mémoire
occupée n'a pas été mesurée et la pointe reste à sonder.

## 7. Plan

Les durées sont mes estimations, pour une personne qui travaille avec Claude Code. Elles ne
reposent sur aucune mesure de ton rythme.

### Étape 0 : décider et mesurer (une semaine)

1. Trancher les décisions du chapitre 9.
2. Transformer les sondes de cette étude en outil du dépôt Tracéo, et le faire tourner sept jours
   sur une trentaine de flux : disponibilité, âge du relevé, taille en pointe, taux de concordance,
   couverture face aux horaires théoriques.
3. Lancer les demandes d'accès, longues à obtenir : compte Grand Lyon, jeton CTS, clé Tisséo,
   accès Naolib.

*Fini quand* : un tableau de qualification par réseau, et les décisions consignées.

### Étape 1 : le socle sur Tracéo (deux à trois semaines)

1. Service de collecte séparé, registre, décodeur, compilation des horaires.
2. Routes des réseaux, des lignes, des arrêts, des passages et de l'index, avec ETag et cache
   nginx.
3. Collecte à la demande, supervision, interrupteur par réseau.
4. Deux réseaux pilotes : **Bordeaux**, le plus gros flux et un réseau complet, et **Dijon** ou
   **Brest**, petits et bien tenus.
5. Règle de gestion et pages de documentation, comme pour RG-44.

*Fini quand* : les passages de Bordeaux servis par Tracéo concordent avec l'appli officielle sur
dix arrêts, en pointe.

### Étape 2 : Tracéo Live multi-réseau (deux à trois semaines)

1. Identifiant de réseau dans le modèle, compatibilité des widgets existants, tests.
2. Catalogue, étape « Réseau », modes et pastilles génériques.
3. Mentions par réseau, écran des sources.
4. Mode automatique et format À proximité avec un index par réseau.
5. Fiche Play, README, captures.

*Fini quand* : un widget de Bordeaux et un widget francilien cohabitent sur le même téléphone, et
les widgets posés avant la mise à jour n'ont pas bougé.

### Étape 3 : élargir le groupe 1 (en continu)

Par lots d'une dizaine de réseaux qualifiés par la sonde. Moins d'une journée par lot une fois le
socle en place.

### Étape 4 : les compléments du groupe 2 (deux à cinq jours par ville)

Dans l'ordre : Rennes (API ouverte, le plus simple), Lille (lecteur SIRI), Toulouse (dès la clé
obtenue), Nantes (dès l'accès obtenu). La SNCF est un lot à part, d'environ deux semaines, à cause
du parcours par gare.

### Étape 5 : le groupe 3 (une à deux semaines par ville, après obtention de l'accès)

Lyon d'abord : c'est la plus grande ville manquante et la technique est celle de PRIM.

### Étape 6 : le groupe 4 (trois à quatre semaines pour le client SOAP et Marseille)

À n'ouvrir que si la demande le justifie.

## 8. Recommandations

1. **Faire le cache sur Tracéo.** Les mesures tranchent : volume de données, adresses qui changent,
   sources bridées, confidentialité inchangée. La voie sans cache ne tient que pour de petits
   réseaux, et elle oblige à écrire dans l'appli tout ce que le serveur ferait mieux.
2. **Séparer le service des villes du site francilien.** Un flux tiers en panne ou trop gros ne
   doit pas ralentir gotraceo.fr.
3. **Commencer par Bordeaux et un petit réseau**, puis élargir par lots. Ne pas viser l'exhaustif :
   chaque réseau ajouté est un réseau à surveiller, pour un projet bénévole.
4. **Ne pas annoncer Toulouse, Rennes ni Lille sans leur métro.** Une ville annoncée sans son mode
   principal déçoit plus qu'une ville absente.
5. **Lancer dès maintenant les demandes d'accès pour Lyon et Nantes.** Elles ne coûtent rien et
   leur délai ne dépend pas de toi.
6. **Garder le contrat figé de la route actuelle** et créer des routes nouvelles, plutôt que
   d'ajouter un paramètre à `/api/widget/passages` : sa clé de cache nginx ne connaît que la ligne
   et l'arrêt.
7. **Rester honnête à l'écran**, comme le veut le projet : relevé daté, ligne sans temps réel
   signalée, mention de la source et de la licence par réseau.
8. **Ne pas étendre le site Tracéo lui-même.** Les statistiques, la carte et les itinéraires
   reposent sur PRIM. Le site ne gagnerait qu'une page des réseaux couverts et de leurs sources.
9. **Ne pas faire dépendre la sortie Play de ce chantier.** C'est mon avis, pas une mesure : le
   test fermé en cours porte sur la version francilienne, et le multi-ville ferait une bonne
   version suivante. Il élargirait aussi le vivier de testeurs hors Île-de-France.

## 9. Décisions à arbitrer

| | Question | Ma proposition |
|---|---|---|
| D1 | Cache sur Tracéo ou téléphone en direct ? | Cache |
| D2 | Dans le processus du site ou dans un service séparé ? | Service séparé |
| D3 | Collecte en continu ou à la demande ? | À la demande, avec mise en sommeil |
| D4 | Quels réseaux pilotes ? | Bordeaux et Dijon, sauf si tu as des testeurs ailleurs |
| D5 | La fiche Play et le README restent-ils « Île-de-France » ? | Passer à une liste de villes au moment de l'étape 2 |
| D6 | Faut-il afficher les horaires théoriques quand le temps réel manque ? | Non pour l'instant |
| D7 | La SNCF entre-t-elle dans le périmètre ? | Oui, mais après les villes du groupe 1 |
| D8 | Qui crée les comptes chez les exploitants ? | Toi : je ne peux ni créer de compte ni accepter des conditions à ta place |

## 10. Risques

| Risque | Parade |
|---|---|
| Un flux change d'adresse ou s'arrête | Registre sur le serveur, supervision, mise en sommeil automatique |
| Les identifiants ne concordent plus après une nouvelle grille horaire | Rechargement déclenché par le taux de courses non retrouvées |
| Le proxy national est un point de passage unique pour 71 réseaux | Garder dans le registre l'adresse d'origine de chaque flux |
| Le volume en pointe dépasse la mesure de ce soir | Remesurer à l'étape 0 ; collecte à la demande |
| La qualité varie d'un réseau à l'autre (à Nice, un tiers des courses n'ont pas de destination ; à Bordeaux, 73 lignes sur 202 n'ont pas de couleur) | Sonde de qualification, valeurs de repli, réseau refusé sous un seuil |
| Une licence impose plus que prévu | Lire les conditions de chaque portail avant d'ouvrir un réseau |
| La charge de suivi grandit avec le nombre de réseaux | Élargir par lots, retirer un réseau mal tenu |

## Annexe A : flux sondés le 11 novembre 2026, de 18 h 57 à 19 h 04

Taille brute, taille réellement transférée, âge du relevé, nombre de courses, nombre de passages
(arrêts de toutes les courses), nombre de lignes présentes.

| Réseau | Brut | Transféré | Âge | Courses | Passages | Lignes |
|---|---|---|---|---|---|---|
| Bordeaux (TBM) | 965 Ko | 965 Ko | 11 s | 734 | 26 097 | 45 |
| SNCF (TER, TGV, Intercités) | 898 Ko | 164 Ko | 43 s | 1 337 | 11 809 | non indiquées |
| Normandie (agrégat régional) | 660 Ko | 660 Ko | non lisible | 516 | 10 145 | 83 |
| Toulouse (Tisséo) | 572 Ko | 572 Ko | 4 s | 667 | 9 106 | 40 |
| Nantes (Naolib) | 488 Ko | via proxy | 11 s | 356 | 8 081 | 45 |
| Orléans (TAO) | 266 Ko | 266 Ko | 41 s | 168 | 5 371 | 10 |
| Nice (Lignes d'Azur) | 182 Ko | 182 Ko | 54 s | 325 | 6 111 | 45 |
| Brest (Bibus) | 154 Ko | 49 Ko | 34 s | 202 | 5 040 | 16 |
| Montpellier (TaM) | 139 Ko | 139 Ko | 34 s | 204 | 3 877 | 36 |
| Angers (Irigo) | 134 Ko | 134 Ko | 29 s | 231 | 4 438 | 21 |
| Clermont-Ferrand (T2C) | 133 Ko | 33 Ko | 1 s | 111 | 3 578 | 13 |
| Toulon (Mistral) | 127 Ko | 127 Ko | 106 s | 107 | 3 619 | 23 |
| Dijon (Divia) | 103 Ko | 29 Ko | 4 s | 139 | 2 930 | 18 |
| Le Havre (LiA) | 96 Ko | 96 Ko | 59 s | 120 | 2 795 | 20 |
| Rouen (Astuce, TCAR) | 95 Ko | 45 Ko | 23 s | 139 | 2 489 | 16 |
| Rennes (STAR) | 94 Ko | 23 Ko | 16 s | 105 | 2 951 | 28 |
| Nancy (Stan) | 76 Ko | 23 Ko | 55 s | 86 | 1 845 | 15 |
| Le Mans (SETRAM) | 69 Ko | 69 Ko | non lisible | 64 | 1 570 | 16 |
| Reims | 66 Ko | 66 Ko | 49 s | 89 | 1 871 | 12 |
| Metz (Le Met') | 50 Ko | 15 Ko | 16 s | 81 | 1 304 | 26 |
| Lille (ilévia) | 48 Ko | 16 Ko | 7 s | 153 | 2 024 | 35 |
| Tours (Fil Bleu) | 34 Ko | 34 Ko | 72 s | 33 | 785 | non indiquées |
| Amiens (Ametis) | 32 Ko | 11 Ko | 18 s | 24 | 839 | 10 |
| Lens (Tadao) | 29 Ko | 29 Ko | 44 s | 46 | 840 | 12 |
| Saint-Étienne (STAS) | 22 Ko | 9 Ko | 35 s | 96 | 817 | 17 |
| Mulhouse (Soléa) | 15 Ko | 15 Ko | 42 s | 18 | 415 | 5 |
| Nîmes (Tango) | 7 Ko | 3 Ko | 24 s | 22 | 193 | 10 |

« Non lisible » : l'horodatage du flux est en avance sur l'heure de la sonde (84 s en Normandie,
172 s au Mans), ce qui ne permet pas de dire son âge.

Modes présents dans le flux, pour les réseaux confrontés à leurs horaires théoriques :

| Réseau | Métro | Tram | Autres |
|---|---|---|---|
| Toulouse | 0 sur 2 | 1 sur 1 | téléphérique 1 sur 1 |
| Rennes | 0 sur 2 | sans objet | |
| Lille | 0 sur 2 | 1 sur 1 | |
| Bordeaux | sans objet | 6 sur 6 | bateau 3 sur 3 |
| Nice | sans objet | 4 sur 4 | transport à la demande 0 sur 9 |
| Tours | sans objet | 1 sur 1 | |

Autres accès testés :

| Accès | Réponse |
|---|---|
| Lyon, SIRI Lite | 401, compte requis |
| Strasbourg, SIRI Lite | 401, jeton requis |
| Marseille RTM, SIRI SOAP | 200 à une demande d'état, sans clé |
| Grenoble, GTFS-RT du catalogue | 403 |
| Grenoble, API du réseau | Liste des lignes ouverte ; passages refusés sans en-tête d'origine |
| Nantes, SIRI Lite | 401 |
| Nantes, GTFS-RT par le proxy national | 3 réponses sur 10 essais espacés de 35 à 41 secondes |
| Bordeaux, SIRI Lite par arrêt | 200, avec la clé publique du catalogue |
| Rennes, API ouverte | 200 pour les bus et pour le métro, sans clé |
| Caen, SIRI SOAP | 429, « 1 requête par 10 minutes » |
| Besançon, API | Clé requise |
| Toulouse, API Tisséo | 401, clé requise |
| Lille, SIRI par le proxy national | 200, 6,2 Mo, métro ligne 1 présent |
| SNCF, SIRI Lite par le proxy national | 200, 5,3 Mo de XML |

## Annexe B : fichiers de Tracéo Live concernés

| Fichier | Ce qui est propre à l'Île-de-France |
|---|---|
| `data/TraceoClient.kt` | Préfixes `line:IDFM:` et `stop_area:IDFM:`, `isTraceoStop`, routes sans réseau |
| `data/Models.kt` | `TransportMode` (RER, Transilien), `Line.lineRef` en `STIF:`, `WidgetConfig` sans réseau |
| `data/PassageSource.kt` | `DataSources.route`, `modes`, `refreshChoices` : tout dépend de la clé PRIM |
| `data/Referential.kt` | `bareLineId` ne garde que la fin de l'identifiant ; open data IDFM |
| `data/StopIndex.kt` | Index construit à partir de quatre jeux IDFM |
| `data/NearbyRules.kt` | `NearbyLine.stopId` en `stop_area:IDFM:`, règle Noctilien |
| `data/Directions.kt` | Retrait des arrivées au terminus par le nom de l'arrêt |
| `data/Prefs.kt` | Favoris et lieux sans réseau ; un seul `traceoNextAt` pour toute l'appli |
| `widget/BadgeRenderer.kt` | Forme de la pastille selon les modes franciliens |
| `ui/ConfigureActivity.kt` | Quatre étapes sans réseau ; ligne de la clé PRIM |
| `res/values/strings.xml` | 29 lignes citent PRIM ou l'Île-de-France, dont `data_mention` |
| `README.md`, `PRIVACY.md`, fiche Play | « en Île-de-France » |

Sur Tracéo, 276 mentions d'IDFM dans 38 fichiers du serveur et la validation des positions sur
l'Île-de-France (`/api/nearby`) confirment qu'il vaut mieux un service à part qu'une
généralisation du code existant.
