# Prompt pour le projet Tracéo : brique MR1, les réseaux à flux ouvert et complet

À coller tel quel dans une session ouverte sur le dépôt Tracéo. Elle sera la session principale :
elle cadre, découpe, délègue, intègre et fait relire.

---

Réalise la brique **MR1** : étendre le module `backend/multireseaux/` (MR0, #128, RG-45, ADR-014),
qui sert aujourd'hui Troyes, aux **21 réseaux à flux GTFS-RT ouvert et complet** de l'étude du
11/11/2026. Tu es la session principale de ce chantier : lis d'abord « Organisation du travail ».

## Contexte

- MR0 est sur `main` : cinq routes `/api/reseaux*` (routes 79 à 83 de `docs/API.md`), registre
  `reseaux.json`, relevé à la demande, horaires compilés dans `data/reseaux/<id>.sqlite`. En
  production le module est coupé tant que `RESEAUX_API=1` n'est pas posé.
- L'appli Tracéo Live (dépôt `Elod10/traceo-live`, branche `multi-reseaux`, PR #18 à #23 ouvertes)
  est construite contre ces cinq routes telles que tu les as documentées. Une session de l'appli
  prépare en parallèle l'affichage d'une vingtaine de réseaux.
- Décision du mainteneur, le 18/11/2026 : servir tous les réseaux du groupe 1 de l'étude, ceux qui
  ne demandent ni compte ni clé personnelle et dont le flux GTFS-RT porte tous les modes du réseau.
- Pour lire le plan côté appli (facultatif, ce prompt se suffit) :
  `git -C "<dossier du dépôt de l'appli>" show origin/reseaux-6-ecrans:chantiers/multi-reseaux/reseaux-ouverts.md`
  et de même pour `etude.md`.

## Les 21 réseaux

Mesures du 11/11/2026 entre 18 h 57 et 19 h 04, **un soir de jour férié** : une seule lecture de
chaque flux. Le jeu se lit à `https://transport.data.gouv.fr/api/datasets/<identifiant>`.
L'adresse du flux est à relire dans les ressources du jeu : je ne la recopie pas ici.

| Ville | Réseau | Identifiant du jeu | Licence | Flux servi par | Flux brut | Arrêts / lignes du jeu | Modes annoncés |
|---|---|---|---|---|---|---|---|
| Bordeaux | TBM | `67f5bad303325228295b7dff` | Licence Ouverte | un serveur du réseau, clé publique dans l'adresse | 965 Ko | 7 422 / 202 | bus, tram, bateau |
| Nice | Lignes d'Azur | `685181f3733800e180c475fa` | Licence Ouverte 2.0 | un serveur du réseau | 182 Ko | 4 499 / 109 | bus, tram |
| Montpellier | TaM | `662ba9a3f7d9ff84a3060d0a` | ODbL | un serveur du réseau | 139 Ko | 1 454 / 44 | bus, tram |
| Rouen | Astuce | `5cd4321f8b4c4137d1244318` | Licence Ouverte 2.0 | un serveur du réseau | 95 Ko | 3 706 / 112 | bus, métro, bateau |
| Toulon | Réseau Mistral | `5b6c1650634f4160a32d2a77` | Licence Ouverte 2.0 | un serveur du réseau | 127 Ko | 2 013 / 97 | bus, bateau, câble |
| Saint-Étienne | STAS | `5c34c93f8b4c4104b817fb3a` | Licence Ouverte 2.0 | un serveur du réseau | 22 Ko | 2 284 / 85 | bus, tram |
| Tours | Fil Bleu | `638157af7f6b7cd00e2908e1` | Licence Ouverte 2.0 | un serveur du réseau | 34 Ko | 2 271 / 50 | bus, tram |
| Clermont-Ferrand | T2C | `6397c0fb7612df845fc8cd1f` | Licence Ouverte 2.0 | le proxy national | 133 Ko | 2 028 / 49 | bus, tram |
| Angers | Irigo | `6178cee254e3b3f0744a1318` | ODbL | un serveur du réseau | 134 Ko | 1 771 / 104 | bus, tram |
| Orléans | TAO | `61fb1b864ba517c310952886` | Licence Ouverte 2.0 | un serveur du réseau | 266 Ko | 1 401 / 43 | bus, tram |
| Dijon | Divia | `5d31d8b69ce2e703da90b699` | Licence Ouverte 2.0 | le proxy national | 103 Ko | 1 127 / 59 | bus, tram |
| Reims | Grand Reims Mobilités | `63b4c3d200fbf8e5ed9dde9a` | Licence Ouverte 2.0 | le proxy national | 66 Ko | 1 210 / 28 | bus, métro |
| Brest | Bibus | `55ffbe0888ee387348ccb97d` | Licence Ouverte 2.0 | le proxy national | 154 Ko | 1 051 / 65 | bus, tram, câble |
| Le Havre | LiA | `617c12b7f9aaa6853cf6d303` | ODbL | un serveur du réseau | 96 Ko | 1 095 / 34 | bus, tram |
| Le Mans | SETRAM | `5acb75f0c751df341652c886` | Licence Ouverte 2.0 | un serveur du réseau, clé publique dans l'adresse | 69 Ko | 1 547 / 65 | bus, tram |
| Metz | Le Met' | `546609c1c751df1a6f6c8d07` | Licence Ouverte 2.0 | le proxy national | 50 Ko | 1 667 / 79 | bus, bateau |
| Nancy | Stan | `63b4c3d2d7857ab0c49dde9a` | Licence Ouverte 2.0 | le proxy national | 76 Ko | 1 466 / 42 | bus |
| Mulhouse | Soléa | `63b4c3d300fbf8e5ed9dde9e` | Licence Ouverte 2.0 | le proxy national | 15 Ko | 1 499 / 48 | bus, tram |
| Amiens | Ametis | `6033a87b4e276fd6499986bf` | Licence Ouverte 2.0 | le proxy national | 32 Ko | 1 452 / 42 | bus |
| Nîmes | Tango | `5c642379634f41657e9b5ea4` | Licence Ouverte 2.0 | un hôte tiers (`bus-tracker.fr`) | 7 Ko | 1 134 / 88 | bus |
| Lens | Tadao | `600ee0e9d319b4c4b84bd748` | ODbL | un serveur du réseau | 29 Ko | 5 279 / 336 | bus |

Les 21 flux pèsent ensemble 2,8 Mo par tour. Relevés chacun une fois par minute toute la journée,
cela ferait environ 4 Go par jour, et sans doute deux à trois fois plus en pointe (estimation) :
le relevé à la demande de RG-45 reste indispensable.

### Ce que la sonde a vu, réseau par réseau

- **Vérifié sur trois réseaux seulement** (Bordeaux, Nice, Tours) : `trip_id` retrouvés dans les
  horaires à 99 ou 100 %, `stop_id` à 100 %, tous les modes présents. Les 18 autres ont seulement
  été lus : leur concordance et la présence de tous leurs modes restent à établir.
- **`start_date`** n'est porté que par Bordeaux, Clermont-Ferrand et Le Mans. Partout ailleurs la
  date de service se déduit, comme à Troyes.
- **`direction_id`** manque dans le flux à Brest (0 %), Angers (16 %), Mulhouse (44 %), Reims
  (48 %), Nancy et Saint-Étienne (51 %), Rouen (52 %). **Tours** ne porte ni `route_id` ni
  `direction_id`. MR0 les relit déjà dans les horaires : à vérifier sur ces réseaux.
- **Plusieurs fichiers d'horaires dans un même jeu** : Rouen (2), Le Mans (2, dont un « flex »),
  Amiens (2, dont un intitulé « pour être utilisé avec le GTFS-RT »). MR0 prend une ressource
  GTFS sans que le registre puisse dire laquelle.
- **Plusieurs ressources temps réel dans un même jeu** (passages, positions et alertes
  confondus) : Rouen en publie 8, Le Havre 5, Nîmes 5, Amiens 4. À Rouen je n'ai lu que le flux
  d'un exploitant (TCAR, 16 lignes vues sur 112) : à vérifier, le réseau entier demande peut-être
  plusieurs flux de passages.
- **Angers** annonce des passages jusqu'à trois jours à l'avance (horizon de 4 383 min).
- **Le Mans** : l'en-tête du flux était en avance de 172 s sur l'heure réelle. RG-45 date les
  réponses avec cet en-tête.
- **Toulon** : relevé vieux de 106 s, le plus ancien des 21.
- **Saint-Étienne** : 149 arrêts sautés sur 817 passages.
- **Nice** : un tiers des courses sans `trip_headsign` (sans effet, la destination est le dernier
  arrêt) ; 9 lignes de transport à la demande absentes du flux.
- **Bordeaux** : 734 courses et 26 097 passages dans un seul relevé ; 2,1 millions de lignes de
  `stop_times` ; 73 lignes sur 202 sans couleur.
- **Nîmes** : le flux inscrit au jeu est servi par `bus-tracker.fr`, qui n'est pas l'exploitant.
  Vérifie qui le produit et sous quelle licence avant de le retenir.
- **Lens** : 336 lignes et 5 279 arrêts pour 12 lignes vues dans le flux : beaucoup de lignes
  scolaires, et une emprise sans doute large. Regarde ce que cela donne dans le choix des lignes.
- **Modes à vérifier** : le catalogue annonce un métro à Reims et un bateau à Metz. Le mode servi
  doit venir du `route_type` des horaires, et être juste.
- L'agrégat régional de Normandie recoupe Rouen et Le Havre : il n'entre pas dans le périmètre.

## Ce que MR0 ne sait pas encore faire (lu dans le code le 18/11/2026)

1. **Mémoire.** `horaires._lire_gtfs` garde tous les `stop_times` dans un dictionnaire : tenable
   pour 152 000 lignes à Troyes, pas pour 2,1 millions à Bordeaux. ADR-014 prévoit une table
   temporaire SQLite.
2. **Séparation.** ADR-014 fixe le seuil : processus à part avant tout flux qui se décode en plus
   de 50 ms ou qui garde plus de 100 Mo. Bordeaux dépasse les deux. Le chemin y est décrit (second
   conteneur de la même image, `location /api/reseaux` dans nginx, point d'entrée propre,
   `parametres_univoques` dans un module pur).
3. **Zones d'arrêt.** `zone_de` retombe sur le quai quand `parent_station` manque : un arrêt
   devient deux stations de même nom, une par sens, et un widget n'en verrait qu'un. Il faut
   regrouper les quais de même nom et proches, avec un identifiant de zone déterministe.
4. **Couleurs.** `couleur()` sert `#FFFFFF` et `#000000` quand le réseau n'en publie pas : l'appli
   ne peut pas distinguer ce blanc d'une vraie couleur.
5. **Registre.** Une entrée ne sait dire ni quel fichier d'horaires prendre, ni qu'un réseau a
   plusieurs flux.

## Vocabulaire commun aux deux projets

L'appli et sa documentation emploient les mêmes mots que RG-45 et `docs/API.md`. Garde-les.

| Notion | Dans les réponses | Chez toi | Dans l'appli |
|---|---|---|---|
| Réseau | `networks[]` : `id`, `name` (nom du réseau), `city` (ville) | une entrée de `reseaux.json` | `Network` |
| Catalogue | `GET /api/reseaux` | le registre, moins ce qui n'est pas compilé | `NetworkCatalog` |
| Emprise | `bbox` : `latMin`, `latMax`, `lonMin`, `lonMax` | calculée, marge de 0,015° | `BoundingBox` |
| Mode | `modes[]` et `mode.key`, parmi `metro`, `tram`, `rail`, `bus`, `boat`, `cable`, `funicular` | `horaires.mode_de` | `TransportMode` |
| Cadence | `refreshSec` | `cadenceS` | `refreshSec` |
| Statut | `status` : `ok`, `stale`, `off` | muet, coupé, endormi | `NetworkStatus` |
| Mention | `attribution` : `source`, `url`, `licence`, `licenceUrl`, `text` | idem | `Attribution` |
| Horaires compilés | `indexAt`, égal au `builtAt` de l'index | `data/reseaux/<id>.sqlite` | `indexAt` |
| Ligne | `id` (opaque, le `route_id`), `code` (pastille), `name`, `color`, `textColor`, `mode` | `lines` | `Line` |
| Station | `stations[].id`, et `zone` dans l'index : une zone d'arrêt, tous les quais, les deux sens | zone | `Stop` |
| Quai | une entrée de `stops[]` dans l'index | quai | `IndexedStop` |
| Relevé | `fetchedAt`, `ageSec`, `fresh`, `nextAt` | relevé | `PassageSnapshot` |

L'Île-de-France ne figure pas au catalogue : l'appli la connaît d'office sous `idfm`, identifiant
que tu ne dois donc jamais attribuer.

## Contrat : ce qui est figé, ce qui peut évoluer

**Figé.** Les routes 79 à 83, leurs champs, leurs codes d'erreur et leurs en-têtes, tels que
`docs/API.md` les décrit aujourd'hui. `/api/lines`, `/api/stations` et `/api/widget/passages` ne
changent pas (RG-44). L'appli compte en particulier sur ceci :

- un `id` de réseau fait de minuscules, de chiffres et de tirets, commençant par une lettre ou un
  chiffre ; un réseau sans `bbox` est ignoré par l'appli ;
- les sept clés de mode, et aucune autre : trolleybus et car vont dans `bus` ;
- `indexAt` et `builtAt` identiques, en date ISO ;
- `fetchedAt` et `ageSec` nuls avec `fresh` faux tant que le réseau n'a pas été relevé, et
  `nextAt` à l'heure du réveil ;
- 404 pour un réseau, une ligne ou un arrêt inconnus, 503 pour un réseau coupé ou des horaires pas
  encore compilés ;
- un identifiant servi ne change plus : l'appli le garde dans la configuration des widgets. Pour
  retirer un réseau, coupe-le (`RESEAUX_OFF`, statut `off`) plutôt que de l'ôter du registre.

**Évolutions que je te demande.** Toutes compatibles avec l'appli telle qu'elle est codée.

1. `color` et `textColor` valent `null` quand le réseau n'en publie pas. L'appli met alors la
   couleur du mode. Une couleur publiée est servie telle quelle, même blanche.
2. Une station existe toujours pour les deux sens d'un arrêt, même sans `parent_station`.
3. Le catalogue est trié par `city` : avec 22 entrées, son ETag ne doit pas dépendre d'un ordre
   de chargement.
4. `realtime` reste facultatif. Ne sers `false` que pour une ligne observée absente du flux sur au
   moins sept jours.
5. Rien d'autre sans me le dire. Un champ ajouté ne gêne pas l'appli ; un champ renommé, retiré
   ou d'un autre type la casse.

**Identifiants des réseaux : à arbitrer au cadrage.** Ma proposition : la ville principale, en
minuscules sans accent (`bordeaux`, `saint-etienne`, `le-havre`, `clermont-ferrand`). Une marque
change (l'adresse du flux de Reims dit « citura » quand le jeu s'appelle Grand Reims Mobilités),
une ville non, et
`RESEAUX_OFF=bordeaux` se lit mieux que `RESEAUX_OFF=tbm`. Troyes s'appelle aujourd'hui `tcat` :
dis-moi s'il vaut mieux le renommer `troyes` tant que rien n'est en production, ou le garder en
exception. L'appli n'écrit aucun identifiant en dur, hormis `idfm`.

## Ce que j'attends

1. **Le cadrage et les décisions**, selon `docs/METHODOLOGY.md`, avant de coder : séparation du
   processus (amendement d'ADR-014), budget de mémoire, de processeur et de volume téléchargé sur
   le VPS, règle des identifiants, seuils de qualification, ordre des vagues.
2. **La séparation du module** dans son propre processus, par le chemin d'ADR-014, avant d'ajouter
   un gros flux. Le site ne doit ni ralentir ni tomber à cause d'un réseau tiers.
3. **Une compilation à mémoire bornée**, étalée dans la journée : 22 jeux de 2 à 21 Mo ne se
   téléchargent ni ne se compilent tous à la même minute.
4. **La généralisation des horaires** : zones sans `parent_station`, couleurs absentes, choix du
   fichier d'horaires quand le jeu en publie plusieurs, plusieurs agences, types de ligne étendus,
   noms déjà en casse normale, `direction_id` absent.
5. **La généralisation du relevé** : plusieurs flux pour un réseau, date de service sans
   `start_date` (courses d'après minuit comprises), en-tête en avance sur l'heure réelle, horizon
   de plusieurs jours, clé publique dans l'adresse (elle reste dans le registre ou se relit au
   catalogue, jamais dans une réponse ni dans un journal).
6. **Une sonde de qualification**, à partir de `tools/probe_reseau.py` : une commande, un fichier
   de résultats par réseau, sans moi devant l'écran. Elle mesure au moins la concordance des
   `trip_id` et des `stop_id`, la part des passages avec une heure, la part des départs théoriques
   de la demi-heure présents dans le flux, les modes et les lignes vus sur une journée ouvrée
   entière, l'âge et la régularité du relevé, la taille en pointe, la part des quais rattachés à
   une zone et des lignes colorées. Propose les seuils et fais-les-moi valider.
7. **Le registre** : une entrée par réseau **qualifié**, par vagues, des plus petits flux aux plus
   gros, Bordeaux en dernier. Un réseau qui échoue n'entre pas : il figure au tableau de
   qualification avec la raison.
8. **L'exploitation** : supervision par réseau (`traceo_reseau_*`), interrupteurs, limites de
   débit, cache nginx revu pour 22 réseaux, politesse envers les serveurs des exploitants
   (plancher de 30 s, agent identifié, relevé à la demande seulement).
9. **Les mentions** : `attribution` par réseau selon sa licence (Licence Ouverte : source et date
   de mise à jour ; ODbL : en plus, partage à l'identique de la base dérivée), et les mentions
   légales du site. **Lis les conditions de chaque portail** avant d'ouvrir un réseau : l'étude ne
   les a pas lues.
10. **Les tests sans réseau** : une fixture par particularité (jeu sans `parent_station`, lignes
    sans couleur, flux sans `route_id`, deux flux pour un réseau, types étendus), pas 21 réseaux
    entiers dans le dépôt.
11. **La documentation** : `docs/API.md`, RG-45 ou une règle nouvelle, `docs/ARCHITECTURE.md`,
    `docs/GLOSSARY.md`, `DEPLOY.md`, `ROADMAP.md`, et le tableau de qualification.

## Organisation du travail

Tu es la session principale. Sers-toi de sous-agents, ou de sessions séparées que tu orchestres,
chaque fois que cela fait gagner du temps sans risquer la qualité.

- **Tu gardes** : le cadrage, les décisions, le contrat, le registre `reseaux.json`,
  l'intégration, la documentation finale et tout échange avec moi.
- **Avant de répartir**, fixe les interfaces entre les morceaux (schéma d'une entrée du registre,
  signature de la compilation, forme du relevé) : c'est ce qui permet de travailler en parallèle.
- **Morceaux indépendants**, à confier chacun à un sous-agent ou à une session dans son propre
  worktree :
  - la séparation du processus et son déploiement (point d'entrée, compose, nginx, `DEPLOY.md`) ;
  - les horaires (`horaires.py`, ses tests, ses fixtures) ;
  - le relevé et le registre (`service.py`, `gtfsrt.py`, `registre.py`, `amont.py`, leurs tests) ;
  - la sonde et sa campagne, qui ne font que lire le module. Lance-la en premier et en tâche de
    fond : elle dépend du réseau et de l'heure.
- **Deux rédacteurs ne touchent jamais le même fichier.** Si deux morceaux en ont besoin, c'est
  toi qui l'écris, ou ils passent l'un après l'autre.
- **Chaque délégation reçoit un brief qui se suffit** : le but, les fichiers, ce qui est déjà
  décidé, ce qui est hors périmètre, les tests à lancer, ce que « terminé » veut dire. Elle te
  rend son diff, ce qu'elle a vérifié et ce qu'elle n'a pas pu vérifier.
- **Ne délègue pas** ce qui tient en quelques appels, ni une vérification que tu peux lancer
  toi-même. Un compte rendu de sous-agent n'est pas une preuve : relis le diff et relance les
  tests avant d'intégrer.

### La relecture

Une fois l'ensemble intégré et les tests au vert, ouvre une **session de relecture au contexte
neuf**. Donne-lui le diff de la brique, ce prompt, RG-45 et ADR-014, pas tes conclusions. Elle
cherche à prendre le travail en défaut sur :

- le contrat : chaque champ servi face à `docs/API.md`, sur un petit et un gros réseau ;
- l'isolement : aucun import du code francilien, aucun état partagé, `/api/health` indépendant ;
- les entrées non fiables : archive piégée (taille décompressée, chemins), flux tronqué ou
  malformé, redirection, adresse du catalogue qui change ;
- la mémoire et le temps : compilation de Bordeaux, 22 fils, verrous, fuite au rechargement ;
- les cas limites : minuit, changement d'heure, course ajoutée, arrêt sauté, réseau endormi,
  flux muet, horaires périmés ;
- les secrets et les journaux : aucune clé d'adresse dans une réponse, un journal ou une métrique ;
- l'écart entre le code, les tests et la documentation.

Elle rend une liste de constats classés par gravité, chacun avec le scénario qui le déclenche. Tu
corriges, puis tu lui fais relire les corrections. Je veux voir sa liste et ce que tu en as fait.

## Hors périmètre

- Les réseaux des autres groupes de l'étude : Toulouse, Rennes, Lille, Nantes, la SNCF, Lyon,
  Strasbourg, Grenoble, Marseille.
- Les villes annoncées par le catalogue mais jamais sondées (Perpignan, Dunkerque, Avignon…) :
  la sonde servira à les qualifier dans une brique suivante.
- Aucun écran du site. Aucune modification des routes actuelles ni de la collecte PRIM.
- Pas d'horaire théorique servi seul, pas d'alertes ni de positions de véhicules.

## Méthode

Suis `docs/METHODOLOGY.md` et les règles du dépôt. Soumets-moi les décisions du cadrage avant de
coder. Déploie en préproduction seulement : la production attend mon accord, réseau par réseau si
je le demande.

## Terminé quand

- Le module tourne dans son propre processus en préproduction, et tous les tests passent.
- Chaque réseau a sa ligne au tableau de qualification, mesuré un jour ouvré en pointe ; les
  réseaux qualifiés répondent sur les cinq routes en préproduction.
- Pour trois réseaux de formes différentes (un petit, un réseau à tram, Bordeaux), dix arrêts ont
  été comparés à l'information officielle, résultat noté.
- La relecture est faite, ses constats sont traités ou explicitement écartés.
- Tu m'as donné, pour la session de l'appli : la liste des réseaux servis (identifiant, nom, ville,
  modes), un exemple réel de chaque réponse pour trois réseaux de formes différentes, et la liste
  de tout ce qui s'écarte de ce prompt.
