# Pilote multi-réseaux : Troyes (TCAT)

Plan du 11 novembre 2026. Il fait suite à l'[étude](etude.md) et fixe ce que font l'appli
Tracéo Live, le serveur Tracéo et l'utilisateur pour afficher un premier réseau hors
Île-de-France.

## 1. Cadre

| | |
|---|---|
| Réseau pilote | TCAT, Troyes Champagne Métropole |
| Branche d'intégration | `multi-reseaux`, coupée de `main` |
| Version visée | 3.0 de l'appli |
| Règle sur `main` | Elle reste publiable : le test fermé Google Play porte sur la 2.0.x |
| Appli | Une session dédiée, dans ce dépôt, PR vers `multi-reseaux` |
| Serveur | Une session dans le dépôt Tracéo, d'après [prompt-traceo.md](prompt-traceo.md) |

## 2. Troyes : ce qui a été vérifié

Sonde du 11 novembre 2026 à 19 h 35, un soir de jour férié.

| Point | Résultat |
|---|---|
| Flux temps réel | GTFS-RT relayé sans clé par le proxy national ; 8,4 Ko (2,7 Ko compressé) ; relevé vieux de 30 s |
| Contenu à cette heure | 16 courses, 235 passages, 8 lignes sur 61, 207 arrêts |
| Concordance avec les horaires | `trip_id` 16 sur 16, `stop_id` 207 sur 207, `route_id` 8 sur 8 |
| Heure de passage | Absolue sur 94 % des passages ; les autres sont des arrêts sautés |
| Retard | Absent du flux : à calculer à partir de l'horaire théorique de la course, ou à ne pas afficher |
| Horaires théoriques | 2,5 Mo ; 152 220 lignes de `stop_times` ; 6 124 courses |
| Arrêts | 1 133 quais, tous rattachés à l'une des 613 zones d'arrêt |
| Lignes | 61, toutes en bus, toutes avec une couleur ; 56 publiques |
| Index d'arrêts compilé | 46 Ko, 20 Ko compressé, en 0,2 s |
| Emprise des arrêts | Latitude 48,1840 à 48,3989 ; longitude 3,9053 à 4,2441 |
| Flux annexes | Alertes et positions des véhicules publiées aussi (non utilisées dans le pilote) |
| Licence | ODbL : citer la source, et partager la base dérivée aux mêmes conditions |

### Points d'attention propres à Troyes

- **Mesure mince.** Seize courses un soir férié ne disent rien de la couverture en journée. À
  refaire un jour ouvré, en pointe, avant de conclure.
- **Destinations.** La girouette publiée (`trip_headsign`) n'est le nom du terminus que pour 57 %
  des courses. Pour 27 %, c'est le code de la variante (« 2B »). La destination affichée doit être
  le nom du dernier arrêt de la course.
- **Noms en capitales.** Arrêts et lignes sont écrits « ACACIAS », « AEROPORT ». À remettre en
  casse de titre sur le serveur.
- **Lignes techniques.** Cinq lignes appartiennent à une agence nommée « MASQUE » (Atelier,
  Occasionnels…) : à ne pas servir.
- **Adresse des horaires.** Elle porte la date de publication et change donc à chaque mise à jour :
  il faut la relire au catalogue national, pas l'écrire en dur.
- **Ce que Troyes n'éprouve pas.** Un gros flux (Bordeaux pèse cent fois plus), un tram ou un
  métro, un flux qui donne le retard. Bordeaux reste le bon second réseau.

## 3. Qui fait quoi

| Qui | Quoi |
|---|---|
| **Tracéo** (serveur) | Relever le flux de Troyes, compiler ses horaires, servir les cinq routes du contrat |
| **Tracéo Live** (appli) | Devenir multi-réseau : modèle, client, étape « Réseau », mentions, mode automatique |
| **Utilisateur** | Arbitrer les écarts au contrat, vérifier sur place ou avec ses contacts à Troyes, décider des déploiements et de la publication de la branche |

Les deux développements avancent en parallèle : l'appli se construit contre des réponses
enregistrées tant que le serveur n'est pas livré.

## 4. Contrat v0 entre l'appli et Tracéo

Routes nouvelles. Les routes actuelles (`/api/lines`, `/api/stations`, `/api/widget/passages`)
ne changent pas : les APK installés ne se mettent pas à jour seuls.

L'Île-de-France ne figure pas au catalogue : l'appli la connaît d'office sous l'identifiant `idfm`
et garde ses routes actuelles.

Les valeurs des exemples (noms, couleurs, coordonnées, heures) sont illustratives : seule la forme
compte.

### `GET /api/reseaux`

```json
{"version": 1, "networks": [{
  "id": "tcat", "name": "TCAT", "city": "Troyes",
  "bbox": {"latMin": 48.17, "latMax": 48.41, "lonMin": 3.89, "lonMax": 4.26},
  "modes": ["bus"],
  "refreshSec": 60,
  "status": "ok",
  "attribution": {"source": "TCAT, Troyes Champagne Métropole",
                  "url": "https://transport.data.gouv.fr/datasets/donnees-tcat-troyes-champagne-metropole-1",
                  "licence": "ODbL", "licenceUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
                  "text": "Données TCAT, sous licence ODbL, via Tracéo."},
  "indexAt": "2026-11-11T03:10:00Z"}]}
```

- `id` : minuscules, chiffres et tirets ; stable, il sert dans les chemins.
- `bbox` : emprise des arrêts, avec une marge.
- `modes` : parmi `metro`, `tram`, `rail`, `bus`, `boat`, `cable`, `funicular`.
- `refreshSec` : cadence de relevé visée.
- `status` : `ok`, `stale` (flux muet) ou `off` (réseau coupé).
- `indexAt` : date de compilation des horaires ; elle change quand l'appli doit recharger lignes,
  arrêts et index. Elle est égale au `builtAt` de l'index du réseau : l'appli compare les deux.

### `GET /api/reseaux/{id}/lines`

```json
{"lines": [{"id": "2B", "code": "2B", "name": "Bréviandes - Voves", "color": "#F29400",
            "textColor": "#FFFFFF", "mode": {"key": "bus", "label": "Bus"}, "realtime": true}]}
```

- `id` : chaîne opaque, unique dans le réseau seulement. Elle peut contenir une espace ou un
  deux-points : à encoder dans l'URL.
- `realtime` : facultatif ; faux quand le serveur sait que la ligne n'apparaît jamais dans le
  flux.

### `GET /api/reseaux/{id}/stations?line=`

```json
{"stations": [{"id": "1:ACACI", "name": "Acacias", "town": null}]}
```

Une station est une zone d'arrêt : tous les quais de l'arrêt, dans les deux sens.

### `GET /api/reseaux/{id}/passages?line=&stop=`

Même corps que `/api/widget/passages` :

```json
{"passages": [{"destination": "Chartreux", "mission": "", "direction": "0",
               "aimed": "2026-11-11T18:40:00Z", "expected": "2026-11-11T18:42:10Z",
               "delayMin": 2, "status": "delayed", "atStop": false}],
 "fetchedAt": "2026-11-11T18:35:01Z", "ageSec": 20, "fresh": true,
 "nextAt": "2026-11-11T18:36:05+00:00"}
```

- `direction` : `"0"` ou `"1"`, le sens publié par le réseau.
- `destination` : nom du terminus de la course.
- `aimed` : nul si l'horaire théorique est inconnu ; `delayMin` vaut alors 0.
- `status` : `onTime`, `delayed` ou `cancelled`.
- `atStop` : toujours faux, le flux ne le dit pas.
- Les arrivées au terminus sont retirées par le serveur.
- Seuls les passages présents dans le flux temps réel sont servis : pas d'horaire théorique seul.
- Codes d'erreur de la route du widget : 400, 404 (réseau, ligne ou arrêt inconnu), 429, 503.

### `GET /api/reseaux/{id}/index`

```json
{"version": 1, "builtAt": "2026-11-11T03:10:00Z",
 "lines": [{"id": "2B", "code": "2B", "name": "…", "color": "#F29400", "textColor": "#FFFFFF",
            "mode": {"key": "bus", "label": "Bus"}}],
 "stops": [{"zone": "1:ACACI", "name": "Acacias", "town": null,
            "lat": 48.29731, "lon": 4.07412, "lines": [0, 7]}]}
```

Une entrée par quai desservi ; `zone` est l'identifiant de station des autres routes ; `lines`
renvoie aux positions du tableau `lines`.

### Si le contrat doit bouger

La session Tracéo peut avoir à s'en écarter pour respecter les règles de son dépôt. Elle le
signale à l'utilisateur, qui le répercute ici et à la session de l'appli. La version qui fait foi
à la fin est celle que Tracéo documente dans son `docs/API.md`.

## 5. Lots de l'appli

Une PR par lot, vers `multi-reseaux`.

1. **Modèle.** Un identifiant de réseau dans `Line`, `Stop`, `WidgetConfig`, les favoris et les
   lieux mémorisés. Absent d'une configuration enregistrée, il vaut `idfm` : les widgets déjà
   posés se relisent à l'identique.
2. **Source et client.** `TraceoClient` appelle les routes du contrat ; la clé PRIM, son quota et
   son repli ne valent que pour `idfm` ; `nextAt` retenu par réseau.
3. **Configuration.** Étape « Réseau », modes selon le réseau, pastille générique, nom du réseau
   sur l'accueil.
4. **Mentions.** Source et licence par réseau, d'après le catalogue.
5. **Mode automatique et format À proximité.** Index par réseau, réseau déduit de la position,
   changement de ville sans réglage.

### La première étape de la configuration

- La liste des réseaux s'affiche tout de suite, sans mur de permission.
- Si la position est déjà accordée, le réseau le plus proche est présélectionné.
- Sinon un bouton « Trouver mon réseau » demande la position approximative, qui suffit pour
  choisir une ville. L'appli déclare déjà cette permission.
- Le dernier réseau choisi est retenu : l'étape se franchit d'un toucher les fois suivantes.

La position ne peut pas être la seule entrée : on configure souvent un widget pour un lieu où l'on
n'est pas, et un usager qui refuse la position doit pouvoir poser un widget fixe.

### Le mode automatique et le format À proximité

- **Île-de-France : rien ne change.** Le téléphone construit toujours son index à partir de l'open
  data IDFM, sans Tracéo.
- **Autres réseaux.** Le téléphone ne peut pas faire de même : les arrêts sont dans un fichier
  d'horaires de 2,5 Mo à Troyes et de 21 Mo à Bordeaux, et les lignes de chaque arrêt se déduisent
  de 150 000 à 2 millions de lignes. L'index vient donc compilé de Tracéo : 20 Ko pour Troyes.
- **Changement de ville.** À chaque lecture de la position, le téléphone cherche dans le catalogue
  le réseau dont l'emprise contient le point. S'il a changé, il télécharge l'index du nouveau
  réseau s'il ne l'a pas, puis classe les lignes comme d'habitude. Rien à régler.
- **Aucun réseau à cet endroit.** Le widget le dit, au lieu de rester sur l'ancien réseau.
- **Confidentialité.** Le calcul se fait sur le téléphone : la position exacte ne part toujours
  vers aucun serveur. Tracéo apprend seulement qu'un téléphone a demandé l'index d'un réseau, ce
  que la politique de confidentialité devra dire pour la 3.0.
- **Un widget fixe ne bouge pas** : il reste sur sa ligne et son arrêt.

## 6. Lots de Tracéo

Le détail est dans [prompt-traceo.md](prompt-traceo.md). En résumé :

1. Cadrage selon la méthode du dépôt, et décision d'architecture sur l'isolement du module.
2. Décodeur GTFS-RT en Python pur et compilation quotidienne des horaires de Troyes.
3. Relevé du flux, passages en mémoire, les cinq routes du contrat.
4. Cache, limite de débit, interrupteur, supervision, mentions.
5. Préproduction, puis production sur décision de l'utilisateur.

### Réutilisation par le site, plus tard

Le service est pensé pour que le site Tracéo puisse s'en servir un jour, sans que le pilote ne
construise rien pour lui :

- il garde le relevé complet du réseau (les courses et tous leurs arrêts), pas seulement ce que
  lit le widget ;
- ses routes ne sont pas réservées au widget ;
- ses identifiants portent le réseau ;
- il n'importe rien du code francilien.

Ce qu'il apporterait au site : les prochains passages, puis les positions des véhicules et les
alertes, que Troyes publie déjà. Ce qu'il n'apporte pas : les itinéraires, les plans de ligne et
les statistiques de fiabilité, qui reposent sur PRIM.

## 7. Recette

- Les tests unitaires de l'appli passent, avec des tests nouveaux pour la relecture des
  configurations existantes, le choix de la source par réseau, le client du contrat et le
  changement de ville.
- Sur la préproduction de Tracéo, dix arrêts de Troyes comparés à l'information officielle du
  réseau, un jour ouvré, en pointe.
- Sur un téléphone, un widget de Troyes et un widget francilien cohabitent ; un widget posé avant
  la mise à jour n'a pas bougé.
- À Troyes, par l'utilisateur ou ses contacts : le format À proximité trouve les bonnes lignes
  sans réglage.

## 8. Points ouverts

| | Question | Proposition |
|---|---|---|
| P1 | Le module tourne-t-il dans le processus du site ou à part ? | Isolé dans le code dès le pilote ; séparé au déploiement avant d'ajouter un gros flux. À trancher dans le dépôt Tracéo |
| P2 | Relevé en continu ou à la demande ? | À la demande, mise en sommeil après dix minutes sans requête |
| P3 | Affiche-t-on le retard à Troyes ? | Oui si le calcul à partir de l'horaire théorique se vérifie sur place, sinon non |
| P4 | Quand la branche `multi-reseaux` est-elle publiée sur GitHub ? | Quand la première PR est prête. Le dépôt est public : ce dossier le sera aussi |
| P5 | Second réseau | Bordeaux, une fois le pilote recetté |
