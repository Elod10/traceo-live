# Réseaux ouverts : les 21 réseaux du groupe 1

Plan du 18 novembre 2026. Il fait suite à l'[étude](etude.md) et au [pilote Troyes](pilote-troyes.md).

## 1. Décision

Le 18 novembre 2026, l'utilisateur a décidé d'ouvrir Tracéo Live à **tous les réseaux du groupe 1
de l'étude** : ceux dont le flux GTFS-RT est ouvert, sans compte ni clé personnelle, et porte tous
les modes du réseau. Cela fait 21 réseaux, en plus de Troyes.

| Qui | Quoi |
|---|---|
| **Tracéo** (serveur) | Brique MR1 : séparer le module, le généraliser, qualifier les 21 réseaux, les servir. Voir [prompt-traceo-reseaux-ouverts.md](prompt-traceo-reseaux-ouverts.md) |
| **Tracéo Live** (appli) | Lots 7 à 12 ci-dessous : passer d'un réseau en plus à une vingtaine |
| **Utilisateur** | Arbitrer les décisions du cadrage, lancer la sonde en pointe, décider des mises en production |

Les autres groupes restent hors périmètre : Toulouse, Rennes et Lille sans leur métro, Nantes,
la SNCF, Lyon, Strasbourg, Grenoble, Marseille. Les villes annoncées par le catalogue mais jamais
sondées (Perpignan, Dunkerque, Avignon…) viendront dans un lot suivant.

## 2. Où en sont les deux dépôts, constaté le 18 novembre 2026

- **Tracéo** a livré le pilote sur sa branche principale (brique MR0, règle RG-45, décision
  ADR-014) : les cinq routes sont codées, testées et documentées pour Troyes. Le module est actif
  par défaut en local et en préproduction ; en production il est coupé jusqu'à l'accord de
  l'utilisateur.
- **Tracéo Live** a six PR ouvertes vers `multi-reseaux` (#18 à #23), vertes, pas encore
  fusionnées. Rien n'a été essayé sur un téléphone : l'appli interroge la production, où le
  catalogue ne répond pas encore.

## 3. Les 21 réseaux

Mesures du 11 novembre 2026, un soir de jour férié. Les modes sont ceux que le catalogue national
annonce ; ceux que servira Tracéo viendront des horaires de chaque réseau.

| Ville | Réseau | Modes annoncés | Flux | Arrêts / lignes | Licence |
|---|---|---|---|---|---|
| Bordeaux | TBM | bus, tram, bateau | 965 Ko | 7 422 / 202 | Licence Ouverte |
| Nice | Lignes d'Azur | bus, tram | 182 Ko | 4 499 / 109 | Licence Ouverte 2.0 |
| Montpellier | TaM | bus, tram | 139 Ko | 1 454 / 44 | ODbL |
| Rouen | Astuce | bus, métro, bateau | 95 Ko | 3 706 / 112 | Licence Ouverte 2.0 |
| Toulon | Réseau Mistral | bus, bateau, câble | 127 Ko | 2 013 / 97 | Licence Ouverte 2.0 |
| Saint-Étienne | STAS | bus, tram | 22 Ko | 2 284 / 85 | Licence Ouverte 2.0 |
| Tours | Fil Bleu | bus, tram | 34 Ko | 2 271 / 50 | Licence Ouverte 2.0 |
| Clermont-Ferrand | T2C | bus, tram | 133 Ko | 2 028 / 49 | Licence Ouverte 2.0 |
| Angers | Irigo | bus, tram | 134 Ko | 1 771 / 104 | ODbL |
| Orléans | TAO | bus, tram | 266 Ko | 1 401 / 43 | Licence Ouverte 2.0 |
| Dijon | Divia | bus, tram | 103 Ko | 1 127 / 59 | Licence Ouverte 2.0 |
| Reims | Grand Reims Mobilités | bus, métro | 66 Ko | 1 210 / 28 | Licence Ouverte 2.0 |
| Brest | Bibus | bus, tram, câble | 154 Ko | 1 051 / 65 | Licence Ouverte 2.0 |
| Le Havre | LiA | bus, tram | 96 Ko | 1 095 / 34 | ODbL |
| Le Mans | SETRAM | bus, tram | 69 Ko | 1 547 / 65 | Licence Ouverte 2.0 |
| Metz | Le Met' | bus, bateau | 50 Ko | 1 667 / 79 | Licence Ouverte 2.0 |
| Nancy | Stan | bus | 76 Ko | 1 466 / 42 | Licence Ouverte 2.0 |
| Mulhouse | Soléa | bus, tram | 15 Ko | 1 499 / 48 | Licence Ouverte 2.0 |
| Amiens | Ametis | bus | 32 Ko | 1 452 / 42 | Licence Ouverte 2.0 |
| Nîmes | Tango | bus | 7 Ko | 1 134 / 88 | Licence Ouverte 2.0 |
| Lens | Tadao | bus | 29 Ko | 5 279 / 336 | ODbL |

**Cette liste n'est pas acquise.** La concordance avec les horaires et la présence de tous les
modes n'ont été vérifiées que pour Bordeaux, Nice et Tours. Tracéo qualifie chaque réseau avant de
le servir : un réseau peut être écarté. L'appli ne doit donc dépendre d'aucun réseau en
particulier.

## 4. Vocabulaire commun

Les deux projets emploient les mêmes mots. Le serveur les a fixés dans sa règle RG-45 et dans sa
documentation d'API ; l'appli les suit.

| Notion | Dans les réponses de Tracéo | Dans l'appli |
|---|---|---|
| Réseau | `networks[]` : `id`, `name` (nom du réseau), `city` (ville) | `Network` |
| Catalogue | `GET /api/reseaux` | `NetworkCatalog` |
| Emprise | `bbox` : `latMin`, `latMax`, `lonMin`, `lonMax` | `BoundingBox` |
| Mode | `modes[]` et `mode.key`, parmi `metro`, `tram`, `rail`, `bus`, `boat`, `cable`, `funicular` | `TransportMode` ; `cable` devient `cableway` (`Networks.appMode`) |
| Cadence | `refreshSec` | `refreshSec`, `sourceMinutes` |
| Statut | `status` : `ok`, `stale` (flux muet), `off` (réseau coupé) | `NetworkStatus` |
| Mention | `attribution` : `source`, `url`, `licence`, `licenceUrl`, `text` | `Attribution` |
| Horaires compilés | `indexAt` du catalogue, égal au `builtAt` de l'index | `indexAt` |
| Ligne | `id` (opaque), `code` (pastille), `name`, `color`, `textColor`, `mode` | `Line` |
| Station | `stations[].id`, et `zone` dans l'index : une zone d'arrêt, tous les quais, les deux sens | `Stop` |
| Quai | une entrée de `stops[]` dans l'index | `IndexedStop` |
| Relevé | `fetchedAt`, `ageSec`, `fresh`, `nextAt` | `PassageSnapshot` |

À l'écran, l'appli dit « réseau », « ville », « ligne », « arrêt » et « relevé ». Elle ne dit ni
« station » ni « zone ».

## 5. Contrat

Le contrat qui fait foi est celui que Tracéo documente : routes 79 à 83 de son `docs/API.md`.

### Ce que le serveur sert aujourd'hui, là où il s'écarte du contrat v0

| Point | Contrat v0 du pilote | Servi par Tracéo |
|---|---|---|
| Identifiant de ligne | Exemple « 2B » | Le `route_id` du réseau : « 204 » pour la ligne dont le `code` est « 2B » |
| `realtime` | Facultatif | Jamais servi pour l'instant |
| `attribution.text` | Exemple court | La mention complète : « Contient des informations du jeu … » |
| Réseau jamais relevé | Non décrit | `fetchedAt` et `ageSec` nuls, `fresh` faux, `nextAt` à l'heure du réveil, quelques secondes plus tard |
| Réseau coupé | `status` à `off` | En plus : ses routes répondent 503 et `nextAt` est nul |
| Horaires pas encore compilés | Non décrit | Le réseau est absent du catalogue ; ses routes répondent 503 |
| `delayMin` | Positif | Peut être négatif (bus en avance) |
| Passage supprimé | `status` à `cancelled` | En plus : `expected` porte l'heure théorique |
| Nombre de passages | Non borné | 20 au plus, les deux sens mêlés |
| Erreurs 404 | « réseau, ligne ou arrêt inconnu » | « Réseau inconnu. », « Ligne inconnue. », « Arrêt inconnu sur cette ligne. », et « Route inconnue. » pour un identifiant de réseau mal formé |
| Limites par téléphone | Non décrites | Passages : 60 requêtes par minute ; les quatre autres routes ensemble : 30 par minute |

### Ce qui est demandé à Tracéo pour ce lot

1. `color` et `textColor` nuls quand le réseau ne publie pas de couleur. Aujourd'hui le serveur
   sert du blanc et du noir : l'appli ne peut pas les distinguer d'une vraie couleur. À Bordeaux,
   73 lignes sur 202 sont dans ce cas.
2. Une station pour les deux sens d'un arrêt, même quand le réseau ne rattache pas ses quais à une
   zone. Aujourd'hui un tel arrêt donnerait deux stations de même nom.
3. Un catalogue trié par ville.
4. Aucun champ renommé, retiré ou changé de type. Aucune clé de mode en dehors des sept.
5. Un identifiant de réseau ne change plus une fois servi. La règle de nommage est à l'arbitrage
   de l'utilisateur (proposition : la ville) ; Troyes, aujourd'hui `tcat`, pourrait être renommé
   tant que rien n'est en production.

## 6. Lots de l'appli

Une PR par lot, vers `multi-reseaux`, empilées sur `reseaux-6-ecrans`.

7. **Contrat réel.** Tests du client contre les réponses réelles de la documentation de Tracéo :
   identifiant de ligne distinct du code, couleurs nulles, réseau jamais relevé, réseau coupé,
   horaires pas encore compilés, chaque code d'erreur. Un widget dont le réseau se réveille
   revient à `nextAt`, sans attendre une cadence entière. Pour les essais sur téléphone : l'adresse
   de Tracéo d'un build de debug se lit dans un fichier non suivi par git ; le build de release ne
   connaît que la production.
8. **Étape « Réseau » à une vingtaine de réseaux.** Ordre de la liste (réseau du widget, réseau le
   plus proche, dernier choisi, puis les villes par ordre alphabétique), recherche par ville ou
   par nom de réseau, liste qui défile. L'écran du choix de la clé ne liste plus vingt réseaux
   inactifs : l'Île-de-France, et une phrase pour tous les autres.
9. **Modes et pastilles.** Étape du mode et libellés pour le tram, le métro, le bateau, le câble
   et le funiculaire hors Île-de-France ; ordre des modes ; codes de ligne longs dans la pastille ;
   ligne sans couleur à la couleur de son mode ; couleur de texte illisible sur la couleur de la
   ligne.
10. **Arrêts.** Aucun réseau ne donne la commune : recherche et affichage sans elle, et deux
    arrêts de même nom sur une ligne restent distinguables.
11. **À proximité et mode automatique à grande échelle.** Index de plusieurs milliers d'arrêts
    (Bordeaux : 7 422) : taille en mémoire, temps de lecture dans le processus du widget, index
    des réseaux qu'on a quittés. Emprises voisines ou qui se recouvrent.
12. **Mentions à plusieurs réseaux.** Licence Ouverte et ODbL d'après `attribution`, écran des
    sources et mention du bas des écrans quand les widgets posés couvrent plusieurs réseaux.

**À étudier, pas à coder sans accord.** Un réseau qui republie ses horaires peut changer ses
identifiants de ligne et d'arrêt : le widget recevrait alors un 404. L'appli garde le code de la
ligne et le nom de l'arrêt : elle pourrait s'y rattacher seule. À proposer avec ce que cela coûte.

**Hors de ces lots.** La fiche Play, le README et la politique de confidentialité passent de
« en Île-de-France » à une liste de villes : à faire quand la liste des réseaux qualifiés sera
connue, à la préparation de la 3.0.

## 7. Lots de Tracéo

Le détail est dans [prompt-traceo-reseaux-ouverts.md](prompt-traceo-reseaux-ouverts.md). En résumé :

1. Cadrage et décisions : séparation du processus, budgets, identifiants, seuils, vagues.
2. Module dans son propre processus, comme le prévoit sa décision ADR-014.
3. Compilation des horaires à mémoire bornée, généralisée aux formes des 21 réseaux.
4. Relevé généralisé : plusieurs flux par réseau, date de service déduite, horizons longs.
5. Sonde de qualification et campagne un jour ouvré en pointe.
6. Registre rempli par vagues, des plus petits flux aux plus gros.
7. Relecture par une session au contexte neuf.

## 8. Recette

- Les tests unitaires de l'appli passent, avec des tests nouveaux pour chaque lot.
- Sur la préproduction de Tracéo, un widget de chaque forme de réseau : un réseau de bus seul, un
  réseau à tram, Bordeaux.
- Sur un téléphone, des widgets de trois réseaux et un widget francilien cohabitent ; un widget
  posé avant la mise à jour n'a pas bougé.
- L'étape « Réseau » reste lisible avec les 22 réseaux, et avec un catalogue vide.

## 9. Points ouverts

| | Question | Proposition |
|---|---|---|
| R1 | Règle des identifiants de réseau, et sort de `tcat` | La ville ; renommer Troyes tant que rien n'est en production. À trancher dans le dépôt Tracéo |
| R2 | Seuils de qualification d'un réseau | Proposés par Tracéo après une première campagne |
| R3 | Un réseau qualifié sans tous ses modes est-il servi ? | Non : c'est le critère du groupe 1 |
| R4 | Mise en production réseau par réseau ou d'un bloc | Par vagues, après essai sur téléphone en préproduction |
| R5 | Rattachement d'un widget après un changement d'identifiants | À étudier côté appli, voir le chapitre 6 |
