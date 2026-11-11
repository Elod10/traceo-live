# Prompt pour le projet Tracéo : partie serveur du pilote Troyes

À coller tel quel dans une session ouverte sur le dépôt Tracéo.

---

Réalise la partie **serveur** d'un pilote : servir les prochains passages d'un réseau hors
Île-de-France, **Troyes (réseau TCAT)**, à l'appli Android Tracéo Live.

## Contexte

- Tracéo Live (dépôt `Elod10/traceo-live`) n'affiche aujourd'hui que l'Île-de-France. Elle passe
  en multi-réseaux pour sa version 3.0, sur une branche `multi-reseaux`. Une autre session y
  développe la partie appli, en parallèle de toi.
- Une étude du 11/11/2026 a sondé 27 flux GTFS-RT français : ils donnent des heures absolues et
  des identifiants qui concordent à 99 ou 100 % avec les horaires théoriques publiés. Elle
  conclut qu'il faut un cache sur Tracéo, pas un téléchargement du flux par chaque téléphone.
- Un flux GTFS-RT est un relevé global du réseau, comme le SIRI ET de PRIM : un relevé, des
  passages gardés en mémoire, une route légère qui ne déclenche aucun appel amont.
- Pour lire l'étude et le plan (facultatif, ce prompt se suffit) :
  `git -C "<dossier du dépôt de l'appli>" show multi-reseaux:chantiers/multi-reseaux/pilote-troyes.md`
  et de même pour `etude.md`.

## Troyes, mesuré le 11/11/2026 à 19 h 35 (soir de jour férié)

- Jeu au catalogue national : `https://transport.data.gouv.fr/api/datasets/5c0933e88b4c4177a394acd1`
  (licence ODbL, éditeur TCAT).
- Flux temps réel, sans clé :
  `https://proxy.transport.data.gouv.fr/resource/tcat-troyes-gtfs-rt-trip-update`.
  8,4 Ko, relevé vieux de 30 s, 16 courses, 235 passages, 8 lignes. Heure absolue sur 94 % des
  passages, pas de champ de retard. Concordance des `trip_id`, `stop_id` et `route_id` : 100 %.
  Le proxy répond en gzip quand on l'accepte (2,7 Ko transférés).
- Horaires théoriques (GTFS) : 2,5 Mo, 152 220 lignes de `stop_times`, 6 124 courses, 61 lignes
  de bus toutes colorées, 1 133 quais tous rattachés à l'une des 613 zones (`parent_station`).
  Leur adresse porte la date de publication : relis-la au catalogue, ne l'écris pas en dur.
- Pièges relevés :
  - `trip_headsign` n'est le nom du terminus que pour 57 % des courses ; pour 27 % c'est le code
    de la variante (« 2B »). La destination à servir est le nom du dernier arrêt de la course.
  - Noms d'arrêts et de lignes en capitales (« ACACIAS ») : à remettre en casse de titre.
  - Cinq lignes d'une agence « MASQUE » (Atelier, Occasionnels…) : à exclure.
  - Mesure mince : à refaire un jour ouvré en pointe (lignes vues dans le flux, couverture).
- Un décodeur GTFS-RT de 120 lignes en Python pur, sans dépendance, a lu ce flux en 2 ms et un
  flux de 965 Ko en 200 ms : pas besoin de bibliothèque protobuf, donc pas de nouvel ADR de
  dépendance. Champs utiles : horodatage de l'en-tête ; pour chaque course `trip_id`,
  `start_date`, `route_id`, `direction_id`, `schedule_relationship` ; pour chaque arrêt
  `stop_sequence`, `stop_id`, heure et retard d'arrivée et de départ, `schedule_relationship`.

## Contrat v0 à servir

Routes **nouvelles**. `/api/lines`, `/api/stations` et `/api/widget/passages` ne changent pas,
ni leur clé de cache nginx (RG-44). L'Île-de-France ne figure pas dans ce catalogue.

1. `GET /api/reseaux` :
   `{"version":1,"networks":[{"id":"tcat","name":"TCAT","city":"Troyes","bbox":{"latMin":..,"latMax":..,"lonMin":..,"lonMax":..},"modes":["bus"],"refreshSec":60,"status":"ok","attribution":{"source":"..","url":"..","licence":"ODbL","licenceUrl":"..","text":".."},"indexAt":".."}]}`.
   `id` en minuscules, chiffres et tirets. `bbox` : emprise des arrêts avec une marge (mesurée :
   latitude 48,1840 à 48,3989, longitude 3,9053 à 4,2441). `modes` parmi `metro, tram, rail, bus,
   boat, cable, funicular`. `status` : `ok`, `stale` (flux muet) ou `off`. `indexAt` : date de
   compilation des horaires.
2. `GET /api/reseaux/{id}/lines` :
   `{"lines":[{"id","code","name","color","textColor","mode":{"key","label"},"realtime"}]}`.
   `id` est une chaîne opaque (à Troyes : « 2B », « ZI TAD »), unique dans le réseau seulement.
   `realtime` est facultatif : faux quand tu sais que la ligne n'apparaît jamais dans le flux.
3. `GET /api/reseaux/{id}/stations?line=` : `{"stations":[{"id","name","town"}]}`. Une station
   est une zone d'arrêt : tous les quais, les deux sens. `town` peut être nul.
4. `GET /api/reseaux/{id}/passages?line=&stop=` : même corps que `/api/widget/passages`
   (`passages[]` avec `destination, mission, direction, aimed, expected, delayMin, status,
   atStop`, puis `fetchedAt, ageSec, fresh, nextAt`) et mêmes codes d'erreur (400, 404, 429, 503).
   `direction` vaut `"0"` ou `"1"` ; `atStop` toujours faux ; `aimed` nul et `delayMin` à 0 si
   l'horaire théorique est inconnu ; `status` parmi `onTime, delayed, cancelled`. Les arrivées au
   terminus sont retirées par le serveur. Seuls les passages présents dans le flux sont servis.
5. `GET /api/reseaux/{id}/index` :
   `{"version":1,"builtAt":"..","lines":[...],"stops":[{"zone","name","town","lat","lon","lines":[indices]}]}`.
   Une entrée par quai desservi ; `zone` est l'identifiant de station ; `lines` renvoie aux
   positions du tableau `lines`. Le téléphone y cherche les arrêts proches, sans envoyer sa
   position.

Si une règle du dépôt t'oblige à t'écarter de ce contrat, la règle l'emporte : dis-moi
précisément ce qui change, je le transmets à la session de l'appli.

## Ce que j'attends

1. **Un module isolé du code francilien** : aucun import de `idfm` ni de `realtime`, aucun quota
   PRIM, aucun état partagé. Il doit pouvoir tourner dans son propre processus. Dis-moi, avec un
   ADR, s'il vaut mieux le séparer dès le pilote ou seulement avant d'ajouter un gros flux : celui
   de Troyes ne coûte rien, celui de Bordeaux pèse cent fois plus.
2. **Un registre des réseaux** en configuration : identifiant, nom, ville, jeu du catalogue,
   adresse du flux, licence, mention, cadence. Ajouter un réseau plus tard doit se réduire à une
   entrée.
3. **La compilation des horaires** une fois par jour, et dès que la part de `trip_id` non
   retrouvés dépasse un seuil. En cas d'échec, le dernier bon jeu reste servi.
4. **Le relevé du flux** à la cadence du réseau, seulement quand une requête l'a demandé dans les
   dix dernières minutes ; pas plus d'un appel par 30 secondes au proxy national ; le dernier
   relevé reste servi, daté, quand un appel échoue.
5. **Le retard** : calcule `aimed` à partir de l'horaire théorique de la même course (date de
   service, heures au-delà de 24:00, heure de Paris) et vérifie qu'il est plausible avant de le
   servir. Dans le doute, `aimed` nul.
6. **Le même habillage que la route du widget** : lecture en mémoire sans appel amont, ETag et
   304, cache nginx dont la clé contient le réseau, la ligne et l'arrêt, paramètres univoques,
   limite de débit par IP, interrupteur sans déploiement.
7. **La supervision** par réseau : âge du relevé, taux de concordance, erreurs du flux.
8. **Les mentions** : source et licence dans le catalogue, et où il faut sur le site. Lis les
   conditions du jeu avant toute mise en production (ODbL : citation et partage à l'identique de
   la base dérivée).
9. **Les tests sans réseau**, avec un flux et un extrait d'horaires enregistrés comme fixtures.
10. **La documentation** : `docs/API.md`, `docs/ARCHITECTURE.md`, une règle dans
    `docs/RULES.md`.

Pense à la suite sans la construire : le site pourra un jour afficher ces réseaux. Garde donc le
relevé complet (les courses et tous leurs arrêts), pas seulement ce que lit le widget. Troyes
publie aussi ses alertes et les positions de ses véhicules, à la même adresse en remplaçant
`-trip-update` par `-alerts` ou `-vehicle-position` : hors pilote.

## Hors périmètre

- Aucun écran du site. Aucune modification des routes actuelles ni de la collecte PRIM.
- Pas d'horaire théorique servi seul, pas de stockage de statistiques.
- Pas d'autre réseau que Troyes.

## Méthode

Suis `docs/METHODOLOGY.md` : cadrage d'abord, et soumets-moi les décisions à arbitrer avant de
coder (module séparé ou non, nom de la brique, nom des routes si `/api/reseaux` heurte une
convention). Déploie en préproduction seulement ; la production attend mon accord.

## Terminé quand

- Les cinq routes répondent pour Troyes en préproduction, et tous les tests passent.
- Dix arrêts comparés à l'information officielle du réseau TCAT, un jour ouvré, en pointe, avec
  le résultat noté.
- Tu m'as donné, pour la session de l'appli : un exemple réel de chaque réponse dès que les routes
  marchent en local, puis l'adresse de la préproduction et la liste des écarts au contrat.
