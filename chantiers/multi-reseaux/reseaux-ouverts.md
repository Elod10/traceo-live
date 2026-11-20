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
| Mention | `attribution` : `source`, `url`, `licence`, `licenceUrl`, `text`, `updatedAt` | `Attribution` |
| Horaires compilés | `indexAt` du catalogue, égal au `builtAt` de l'index | `indexAt` |
| Ligne | `id` (opaque), `code` (pastille), `name`, `color`, `textColor`, `mode` | `Line` |
| Station | `stations[].id`, et `zone` dans l'index : une zone d'arrêt, tous les quais, les deux sens | `Stop` |
| Quai | une entrée de `stops[]` dans l'index | `IndexedStop` |
| Relevé | `fetchedAt`, `ageSec`, `fresh`, `nextAt`, `waking` | `PassageSnapshot` |

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
| R1 | Règle des identifiants de réseau, et sort de `tcat` | **Tranché par Tracéo (MR1)** : la ville. `tcat` est devenu `troyes` ; `idfm` n'est jamais attribué |
| R2 | Seuils de qualification d'un réseau | Proposés par Tracéo après une première campagne |
| R3 | Un réseau qualifié sans tous ses modes est-il servi ? | Non : c'est le critère du groupe 1 |
| R4 | Mise en production réseau par réseau ou d'un bloc | Par vagues, après essai sur téléphone en préproduction |
| R5 | Rattachement d'un widget après un changement d'identifiants | À étudier côté appli, voir le chapitre 6 |

## 10. Ce que les lots de l'appli ont décidé

Chaque lot ajoute ici ce qu'il a tranché ou découvert. Rien n'a été essayé sur un téléphone : la
préproduction de Tracéo ne sert pas encore ces réseaux, l'appli est construite contre des réponses
enregistrées conformes au chapitre 5.

### Lot 7 : contrat réel

- **Les 404 d'un réseau disent ce qui manque.** L'appli lit le message du serveur : « Réseau
  inconnu. », « Ligne inconnue. », « Arrêt inconnu sur cette ligne. ». Le widget se vide et demande
  à être reconfiguré, avec le motif. Un message qu'elle ne reconnaît pas reste un 404 sans
  précision, traité comme avant.
- **« Route inconnue. » n'est pas la faute du widget.** Ce 404 vient d'un serveur qui ne sert pas,
  ou plus, les routes des réseaux. Le widget garde ses passages et dit « Tracéo ne sert pas ce
  réseau pour le moment », comme sur un 503. Il en va de même d'un 404 qui n'est pas du JSON :
  Tracéo répond toujours en JSON, ce 404 vient donc d'un intermédiaire. Sur la route du catalogue,
  « Route inconnue. » garde son sens : aucun autre réseau.
- **Un 503 d'un réseau** (réseau coupé, routes coupées, horaires pas encore compilés) : un seul
  message, « Ce réseau est momentanément indisponible chez Tracéo », les passages gardés, aucun
  autre appel.
- **Une clé de mode inconnue, ou absente, se range avec les bus**, comme Tracéo y range déjà le
  trolleybus et le car : la ligne reste accessible. Un champ en plus est ignoré.
- **Réveil d'un réseau.** Une réponse sans relevé récent (`fresh` faux) dont le `nextAt` tombe
  dans les 20 secondes est prise pour celle d'un réseau qui dormait. Le widget y revient 2 à
  6 secondes après l'heure annoncée (selon l'installation, pour ne pas revenir tous ensemble), quel
  que soit l'intervalle choisi, même « Manuelle uniquement » : c'est la suite du même toucher. En
  attendant, le widget, sa notification et la tuile montrent l'état de chargement au lieu de
  « Aucun passage ». L'Île-de-France, relevée en continu, n'est pas concernée.
- **Le réveil se décide par widget.** Chaque widget garde l'heure de son retour, d'après ses
  propres réponses : un autre widget du même réseau, servi après le relevé, ne l'annule pas.
- **Deux retours au plus.** Un flux en panne donne aussi des réponses sans relevé récent qui
  annoncent le prochain tout près. Le widget ne revient donc que deux fois de suite, puis reprend
  son intervalle ; l'actualisation ordinaire suivante repart de zéro. Passé 30 secondes après
  l'heure annoncée (écran éteint, retour en échec), le réveil n'est plus attendu.
- **Mode automatique et À proximité sur un réseau endormi.** La première ligne demandée réveille
  le réseau et répond sans passage. Le mode automatique attend alors le premier relevé, 10 secondes
  au plus, avant de choisir une ligne : sans cela, la ligne suivante, demandée après le relevé,
  prendrait la place de la meilleure. Le format À proximité s'arrête au premier lot de lignes et y
  revient d'un bloc. Le retour vers un réseau qui se réveille ne relit pas la position.
- **Limites connues.** Le réveil compare le `nextAt` du serveur à l'horloge du téléphone, comme le
  fait déjà le suivi des relevés franciliens : une horloge décalée de plus de quelques secondes le
  fait manquer, et le widget retombe sur son intervalle. Et rien, dans la réponse, ne distingue un
  réveil d'un flux muet (questions Q2 et Q4).
- **Adresse de Tracéo d'un build de debug.** Elle se lit à la compilation dans `local.properties`,
  que git ne suit pas : `traceo.debugUrl=https://…` (sans chemin). Sans cette clé, et toujours pour
  un build de release, c'est la production. Une adresse en `http://` n'est joignable que par le
  build de debug. Une clé mal écrite donne un avertissement, et la production.

### Lot 8 : étape « Réseau » à une vingtaine d'entrées

- **Ordre.** En tête : le réseau du widget à modifier, puis le plus proche, puis le dernier choisi,
  puis celui qui est coché s'il n'est aucun des trois, chacun une seule fois : le réseau coché
  reste ainsi toujours en vue. Sans aucune de ces raisons, l'Île-de-France reste en tête. Les
  autres suivent par ville, dans l'ordre alphabétique français (accents et casse ne comptent pas) ;
  l'Île-de-France y prend alors sa place, à I. Un réseau coupé reste à sa place, grisé, et ne
  passe devant que pour le widget qui s'y trouve déjà.
- **Recherche.** À partir de 8 réseaux, une recherche apparaît au-dessus de la liste, par ville ou
  par nom de réseau (« bordeaux » ou « tbm »), sans égard aux accents ; l'Île-de-France se trouve
  aussi par « Paris ». L'état d'un réseau et la raison de sa place ne se cherchent pas, et
  « Trouver mon réseau » s'efface dès qu'un texte est saisi. Les réseaux de tête ont alors leur
  propre carte, les autres la leur. En dessous de 8, une seule carte et pas de recherche, comme au
  pilote ; l'ordre, lui, suit la même règle (à Troyes : Troyes, puis l'Île-de-France).
- **« Continuer » ne valide que ce qui se voit.** Si la recherche masque le réseau coché, ou si
  aucun n'est coché, le bouton se grise et dit de toucher un réseau de la liste.
- **Liste redessinée sans sauter.** Quand le catalogue ou la position arrivent, la liste garde sa
  place et le texte cherché. Si le réseau coché vient de changer, elle revient en haut, où il se
  trouve, et la recherche en cours est effacée. Le dernier réseau choisi est coché d'avance même
  quand le catalogue n'arrive qu'après l'ouverture de l'écran.
- **Écran de la clé.** Il ne liste plus que les réseaux qui acceptent une clé personnelle,
  aujourd'hui l'Île-de-France, suivis d'une phrase pour tous les autres : « Les autres réseaux
  n'ont pas de clé à demander : leurs données sont ouvertes, et Tracéo les relève déjà. »
- **Réglages.** Les sources des widgets posés se rangent par ville.

### Lot 9 : modes et pastilles

- **Ordre des modes hors Île-de-France** : Métro, Tramway, Train, Bus, Bateau, puis câble et
  funiculaire. L'ordre du catalogue ne compte pas. L'Île-de-France garde le sien, avec le RER.
- **Libellés.** « Câble » est le nom du Câble C1 francilien : ailleurs, le mode s'appelle
  « Téléphérique », « Funiculaire », ou « Téléphérique / Funiculaire » si le réseau a les deux, ou
  n'annonce ni l'un ni l'autre. Une ligne se titre « Tram B », « Métro A », « Bateau BAT3 »,
  « Téléphérique C ». La petite pastille du mode, elle, garde « Câble » (transport par câble), ou
  « Funi » pour un réseau qui n'a qu'un funiculaire ; son texte se réduit pour tenir dans sa case,
  où « Bateau » débordait.
- **Réseau dont le catalogue ne dit pas les modes** : tous sont proposés, plutôt qu'aucun.
- **Codes longs.** Jusqu'à 7 caractères, le code entre entier dans la pastille, écrit plus petit à
  partir de 4. Au-delà il perd d'abord ses espaces (« Flexo 51 » donne « Flexo51 ») ; s'il ne tient
  toujours pas, il est coupé au milieu avec « … » (« Navette Gare » donne « Nav…are ») : la fin du
  code, qui distingue les lignes d'une même famille, reste. Le titre de la ligne et les lecteurs
  d'écran gardent le code entier. En Île-de-France, le code et la forme des pastilles ne changent
  pas.
- **Ligne sans couleur** : elle prend la couleur de son mode, dans les listes comme dans les
  widgets (bus vert, tram bleu-vert, métro bleu, train violet, bateau bleu, autres gris-bleu).
- **Blanc servi faute de couleur.** Tant que Tracéo sert le blanc et le noir par défaut du GTFS,
  l'appli traite ce couple comme une absence de couleur, hors Île-de-France. Elle le fera encore
  quand le serveur servira `null` : un réseau qui choisirait vraiment du blanc avec un texte noir
  verrait ses lignes à la couleur de leur mode. Un blanc avec un texte d'une autre couleur est
  gardé.
- **Texte illisible.** La couleur de texte publiée n'est gardée que si elle se lit sur la couleur
  de la ligne ; sinon l'appli met du noir ou du blanc, le plus lisible des deux. Les listes de la
  configuration suivent maintenant la même règle que les widgets, Île-de-France comprise : une
  pastille francilienne dont le texte se lisait mal y change donc de couleur de texte. Sur un fond
  à la couleur de la ligne, si le petit texte ne peut pas se lire dans la couleur publiée même en
  retouchant le fond (rouge sur blanc), il passe au noir ou au blanc.
- **Noir servi faute de couleur de texte.** Tracéo sert le noir par défaut du GTFS quand le réseau
  ne publie pas de couleur de texte, même pour une ligne dont il publie la couleur. Hors
  Île-de-France, l'appli ne tient donc pas compte d'un texte noir : elle choisit elle-même entre le
  noir et le blanc, ce qui redonne le noir là où il était le bon choix (question Q5).

### Lot 10 : arrêts

- **Sans commune.** La liste des arrêts d'une ligne n'affiche que leur nom, sans sous-titre vide,
  et se cherche par ce nom (« Rechercher un arrêt »). Rien d'autre ne dépendait de la commune hors
  Île-de-France.
- **Deux arrêts de même nom sur une ligne.** Sans commune, rien ne les sépare dans la liste. Ce
  sont soit les deux sens d'un même arrêt, que le réseau ne rattache pas l'un à l'autre (le cas que
  Tracéo doit regrouper, chapitre 5), soit deux endroits différents (« Mairie » dans deux
  communes). Leur position tranche, lue dans l'index du réseau :
  - à 200 m ou moins l'un de l'autre, de proche en proche, ils ne font qu'un arrêt, qui porte
    leurs identifiants : le widget les interroge tous, et le sens se choisit à l'étape suivante,
    comme en Île-de-France ;
  - plus loin, ils restent deux, chacun avec l'arrêt voisin de la ligne qui le situe : « Près de
    Jean Jaurès ». Le voisin doit avoir un nom unique sur la ligne, être à 800 m au plus, et plus
    près de cet arrêt que de son homonyme ;
  - si l'index manque, ou si aucun voisin ne les départage, ils restent deux et sont numérotés :
    « Arrêt 1 sur 2 de ce nom ».
  La liste se présente ainsi avant et après le regroupement demandé au serveur : après, il ne
  restera que le second cas.
- **Widgets déjà posés.** Un widget posé sur un seul sens retrouve ses sens cochés quand on rouvre
  sa configuration sur l'arrêt qui réunit maintenant les deux. Si le réseau ne connaît plus l'un
  des identifiants d'un arrêt réuni, le widget le dit, même s'il lui reste des passages de l'autre.
  Ce que deviendra un widget posé quand Tracéo regroupera lui-même les quais dépend des
  identifiants qu'il donnera aux arrêts regroupés (question Q6).
- **L'index à la configuration.** Jusqu'ici, l'index d'un réseau n'était demandé que par un widget
  qui suit la position. Il l'est maintenant aussi à la configuration d'un widget fixe, mais
  seulement quand la ligne choisie a deux arrêts de même nom, et s'il manque sur le téléphone ou
  s'il y est périmé. La liste s'affiche sans l'attendre, puis se redessine. Tracéo apprend alors
  qu'un téléphone s'intéresse à ce réseau, ce que la politique de confidentialité devra dire pour
  la 3.0 : elle écrit aujourd'hui que seuls les widgets qui suivent la position téléchargent
  l'index.
- **Limite.** Le mode automatique et le format À proximité prennent, dans un réseau qui ne rattache
  pas ses quais, le quai le plus proche : donc un seul sens, selon le trottoir où l'on se tient,
  tant que Tracéo ne regroupe pas les quais (chapitre 5).

### Lot 11 : À proximité et mode automatique à grande échelle

- **Mesure d'un index à l'échelle du plus gros réseau** (7 422 quais, 202 lignes), fabriqué à la
  forme que sert Tracéo, sur la machine qui lance les tests et non sur un téléphone : réponse de
  901 Ko ; fichier gardé sur le téléphone de 354 Ko ; téléchargement, décodage et écriture en 0,27 s
  environ, pour 28 Mo alloués au total pendant l'opération ; recherche autour d'un point en 2 ms, qui
  ne garde que les quais du voisinage (90 sur 7 422) et saute les autres sans les décoder. Un
  téléphone sera plusieurs fois plus lent : cela reste loin du temps laissé à un widget. Le
  téléchargement n'a lieu qu'à l'arrivée dans une ville, puis à chaque nouvelle publication des
  horaires ; la recherche, à chaque position lue.
- **Limite du client HTTP.** Il lisait une réponse entière en mémoire, sans borne. Une réponse de
  plus de 8 Mo, une fois décompressée, est maintenant refusée (pas tronquée) : c'est neuf fois le
  plus gros index attendu. Le téléphone garde alors ce qu'il avait.
- **Index des réseaux quittés.** Un widget qui suit la position télécharge l'index de chaque ville
  où il passe, et le renouvelait ensuite à chaque publication, pour toujours. L'index d'un réseau
  n'est plus gardé que s'il sert : un widget posé s'y trouve, ou il a servi depuis moins de
  30 jours (recherche d'arrêts autour de l'usager, ou configuration) et son réseau est au
  catalogue. Sinon il est supprimé, et sera retéléchargé au retour dans la ville. Le ménage passe
  une fois par jour au plus, après un toucher d'un widget qui suit la position ou après
  l'actualisation d'un widget hors Île-de-France. L'index de l'Île-de-France, que le téléphone
  construit lui-même, n'est pas concerné.
- **Emprises qui se recouvrent.** Les emprises sont des rectangles : celle d'un réseau étendu peut
  contenir un coin où il n'a aucun arrêt, et deux réseaux voisins se chevauchent. Le réseau le plus
  local qui a un arrêt tout près l'emporte toujours. Si son arrêt le plus proche est loin, un réseau
  plus étendu passe devant quand le sien est plus près de plus de 400 m, la tolérance du
  classement. Sur ce doute, l'index du réseau étendu est téléchargé s'il vient de Tracéo (une
  requête légère) ; celui de l'Île-de-France, que le téléphone construit à partir de plusieurs
  fichiers, n'est lu que s'il est déjà là. Le format À proximité aligne sa configuration sur le
  réseau de ses lignes à chaque actualisation, pour sa cadence et ses mentions.
- **Limite.** Le long de la ligne où les deux arrêts sont à 400 m d'écart, un widget qui suit la
  position peut passer d'un réseau à l'autre d'une lecture à la suivante : rien ne retient le
  réseau qu'il montrait déjà.
- **Emprises voisines.** Entre deux emprises qui ne se touchent pas, aucun réseau ne couvre le
  point : le widget dit « Aucun réseau ici ». À la configuration, le message ne cite plus tous les
  réseaux couverts, mais le plus proche et sa distance (« Le plus proche : Troyes, à 35,0 km »).
  La distance est celle du bord de l'emprise du réseau, pas du centre de la ville.

### Lot 12 : mentions à plusieurs réseaux

- **La licence vient du catalogue.** Chaque réseau est cité avec le nom et le lien de sa source et
  de sa licence, tels que `attribution` les donne : l'appli n'écrit le nom d'aucune licence.
  « Licence Ouverte » porte déjà le mot : la mention dit « aux conditions de la Licence Ouverte
  2.0 », et garde pour l'ODbL la formule de sa section 4.3, « aux conditions de la licence ODbL ».
- **Écran Sources et licences.** Un paragraphe par réseau des widgets posés, rangés par ville.
  Quand Tracéo sert la mention complète du réseau (« Contient des informations du jeu… »), l'écran
  ne la redit pas avec d'autres mots : il la fait suivre de « Source : … Licence : … », avec leurs
  liens, puis de la date des horaires compilés. La Licence Ouverte demande la date de mise à jour
  des données réutilisées : l'appli n'a que celle de la compilation par Tracéo, et le dit ainsi
  (question Q8).
- **Mention du bas des écrans.** Un seul autre réseau : sa mention entière, comme au pilote.
  Plusieurs : une phrase pour tous, rangés par ville, « Autres réseaux : contient des informations
  des réseaux TBM (Licence Ouverte 2.0) et TCAT (ODbL), relevées par Tracéo. Détail dans Sources et
  licences. », chaque nom de réseau lié à sa source et chaque licence à son texte. Sans widget en
  Île-de-France, elle commence par « Contient ». Au-delà de cinq réseaux, elle nomme les quatre
  premiers et compte les autres, que l'écran des sources cite tous. La mention de l'Île-de-France
  ne change pas.
- **Réseaux cités.** Ceux des widgets posés ; un widget À proximité compte aussi pour le réseau
  dont il montre les lignes, s'il n'est pas encore celui de sa configuration. L'écran des sources
  ne nomme chaque éditeur qu'une fois.
- **Accords.** « d'Irigo », « de TBM » ; le mot « licence » n'est pas répété, où qu'il soit dans le
  nom de la licence.

### Lot 13 : contrat MR1 de Tracéo

Le 20 novembre 2026, la préproduction de Tracéo sert la brique MR1 : dix réseaux, tous « à
l'essai » (un réseau à l'essai peut encore être retiré). Ce lot aligne l'appli sur ce qu'elle sert.
Il a été vérifié contre elle : le client de l'appli a lu, pour les dix réseaux, le catalogue, les
lignes, les arrêts d'une ligne, les passages, l'index et les trois 404 d'un réseau.

- **Les 404 se lisent à leur `code`** : `network`, `line`, `stop`, `route`. Un message reformulé
  ne change plus rien. Sans `code`, l'appli lit le message comme avant ; un `code` qu'elle ne
  connaît pas l'emporte sur un message qu'elle connaît, et donne un 404 sans précision.
- **Le réveil se lit à `waking`.** Vrai : le widget revient à l'heure annoncée. Faux : il ne revient
  pas, même si le relevé est ancien et le prochain annoncé tout près (flux en panne, relevé en
  retard, que Tracéo annonce maintenant à 30 secondes). La déduction du lot 7 ne sert plus que si le
  champ manque. Les retours restent bornés à deux. Mesuré en préproduction : le premier relevé est
  là une à deux secondes après la première requête ; le widget revient 5 à 8 secondes après.
- **Le réveil ne dépend plus de l'horloge du téléphone.** Pendant un réveil, `nextAt` est ramené à
  l'heure du téléphone d'après l'en-tête `Date` de la même réponse : `nextAt` moins `Date`, deux
  secondes en préproduction, ajoutées à l'heure de réception. Sans en-tête lisible, l'heure du retour
  est bornée entre la réponse et 20 secondes après. C'est la réponse de l'appli à Q2 : rien à ajouter
  côté serveur. Hors réveil, `nextAt` reste comparé à l'horloge du téléphone, comme en Île-de-France.
- **Date du jeu de données.** `attribution.updatedAt` est lu et gardé dans le catalogue du téléphone.
  L'écran Sources et licences dit « Jeu de données mis à jour le … », la date que demande la Licence
  Ouverte ; sans elle, il garde « Horaires compilés par Tracéo le … ».
- **Couleur de texte.** Tracéo sert `null` quand le réseau ne publie pas de couleur de texte
  (Saint-Étienne et Dijon, toutes leurs lignes) : l'appli choisit le noir ou le blanc. Un noir servi
  est donc un choix du réseau, gardé s'il se lit, comme en Île-de-France : la règle du lot 9 qui
  l'ignorait est retirée. Relevé le 20 novembre : sur les 138 lignes à texte noir publié, le noir
  est partout le plus lisible des deux, rien ne change à l'écran. Le blanc avec un texte noir reste
  compté comme une absence de couleur (lot 9) ; aucun réseau n'en sert.
- **Stations qui réunissent leurs quais.** Tracéo répond pour la station entière, quel que soit le
  quai demandé : un arrêt que l'appli avait réuni de deux quais (lot 10) recevrait chaque passage
  deux fois. Hors Île-de-France, les doublons sont retirés, après le filtre des sens, qui se lit par
  quai. Le regroupement de l'appli (200 m) ne se déclenche plus, Tracéo regroupant jusqu'à 500 m ;
  il reste pour un serveur qui ne le ferait pas. Un widget posé sur un seul quai reçoit maintenant
  toute la station, les deux sens.
- **Identifiants de réseau.** `tcat` est devenu `troyes` ; l'appli n'en écrit aucun, seuls ses tests
  changent. Un widget posé sur `tcat` reçoit « Réseau inconnu » et demande à être refait : il n'en
  existe que sur les téléphones d'essai.
- **Rien à changer** pour `indexAt`, qui ne change plus qu'avec le référentiel (Q7) ; le début de
  `attribution.text` (Q9) ; le tri du catalogue par ville ; `refreshSec` à 120 ; un réseau coupé
  gardé au catalogue en `off`, déjà grisé à l'étape « Réseau » ; l'en-tête `Etag` écrit autrement
  par le relais de la préproduction (l'appli lit les en-têtes sans tenir compte de la casse, et ne
  se sert pas de l'ETag).
- **Limite : un réseau aux horaires périmés.** À Troyes et à Tours, les horaires publiés ne
  correspondent plus au flux : le relevé est frais, ne donne aucun passage, et le catalogue dit
  `stale`. Le widget dit « Aucun passage », ce qui est faux : il ne relit le catalogue qu'une fois
  par jour, et la réponse des passages ne porte pas le statut (question Q10).

## 11. Questions à Tracéo

Ce qui manquait à l'appli dans le contrat. La brique MR1 (20 novembre 2026) a répondu à toutes,
sauf Q3, reportée ; Q10 est nouvelle.

| | Question | Réponse de Tracéo | Dans l'appli |
|---|---|---|---|
| Q1 | Un code stable dans le corps des 404 | Fait : `code` vaut `network`, `line`, `stop` ou `route` ; le message ne change pas | Lu (lot 13) ; le message reste un repli |
| Q2 | Un délai relatif à côté de `nextAt`, pour ne pas dépendre de l'horloge du téléphone | Proposé : `max-age` moins l'en-tête `Age` ; il faudrait porter le plafond de `max-age` de 60 secondes à la cadence | Pas nécessaire : pendant un réveil, `nextAt` moins l'en-tête `Date` (lot 13). Réponse proposée : laisser le plafond |
| Q3 | De quoi situer un arrêt dans la réponse des arrêts d'une ligne | Reporté : `lat` et `lon` du centre de la station, en ajout | Pour distinguer deux arrêts de même nom, elle télécharge l'index entier du réseau |
| Q4 | Un indicateur explicite de réveil | Fait : `waking`, faux dès la fin du premier appel au flux, réussi ou non | Lu (lot 13) ; la déduction du lot 7 reste un repli |
| Q5 | `textColor` nul quand le réseau ne publie pas de couleur de texte | Fait ; Tracéo demande si l'appli préfère `null` ou `#000000` | `null` : l'appli choisit le contraste (lot 13) |
| Q6 | L'identifiant d'un arrêt regroupé | La zone publiée par le réseau si elle existe, sinon le plus petit quai du groupe ; `stop=` accepte aussi un quai de la station, ou une zone qu'elle a absorbée | Un widget posé sur un quai reçoit toute la station sans rien dire ; doublons retirés (lot 13) |
| Q7 | `indexAt` change-t-il à chaque compilation ? | C'était le cas ; corrigé : il ne change qu'avec les lignes, les stations, l'index, l'emprise ou les modes. Pas encore observé sur plusieurs jours | Rien à changer |
| Q8 | La date de mise à jour du jeu de données | Fait : `attribution.updatedAt`, ou `null` | Écran des sources (lot 13) |
| Q9 | `attribution.text` commence-t-il toujours par « Contient des informations » ? | Garanti | Rien à changer |
| Q10 | Le statut du réseau dans la réponse des passages (`status`, en ajout), pour qu'un widget sache que le temps réel est interrompu sans relire le catalogue | À poser | « Aucun passage » à Troyes et à Tours tant que leurs horaires sont périmés |

## 12. Proposition : rattacher un widget après un changement d'identifiants

Point « À étudier » du chapitre 6 (R5). Rien n'est codé : c'est une proposition, à trancher.

**Le problème.** Un réseau qui republie ses horaires peut changer les identifiants de ses lignes et
de ses arrêts. Le widget reçoit alors un 404, se vide et demande à être reconfiguré. Depuis le lot 7
il sait lequel manque : la ligne, ou l'arrêt sur cette ligne.

**Ce que l'appli a pour s'y rattacher seule** : le code de la ligne, son nom et son mode ; le nom de
l'arrêt ; les sens cochés, par destination.

**Ce qu'elle ferait.** Sur un 404 « ligne » ou « arrêt » hors Île-de-France, une seule fois par
publication d'horaires (`indexAt`) : redemander les lignes du réseau, y chercher la ligne de même
code et de même mode ; redemander ses arrêts, y chercher l'arrêt de même nom ; réécrire la
configuration du widget et l'actualiser. Elle ne se rattache que si chaque recherche donne une
réponse et une seule ; sinon elle demande à être reconfigurée, comme aujourd'hui. Un widget qui suit
la position n'en a pas besoin : il rechoisit sa ligne à la prochaine position.

| | |
|---|---|
| Code de l'appli | Environ 150 lignes sans Android (la recherche, testable seule), 60 dans l'actualisation (déclenchement, garde, écriture sous verrou) |
| Tests | Environ 200 lignes, une quinzaine de cas : code réutilisé par une autre ligne, deux arrêts de même nom, arrêt renommé, sens disparu, réseau renommé |
| Requêtes | Deux par widget rompu et par publication (lignes, puis arrêts de la ligne) ; aucune en temps normal. Sous la limite de 30 par minute, sauf dizaines de widgets rompus à la fois |
| Travail | Un lot, une PR : de l'ordre d'une session comme celle d'un des lots 7 à 12, relecture comprise |
| Côté Tracéo | Rien d'obligatoire. Utile : le code stable des 404 (Q1) ; mieux, la liste des anciens identifiants d'une ligne ou d'un arrêt, qui rendrait le rattachement certain au lieu de probable |

**Les risques.** Se rattacher à tort : un code repris par une autre ligne après une refonte du
réseau, un arrêt renommé dont le nom est celui d'un autre. Le widget montrerait alors, sans rien
dire, les passages d'une autre ligne. L'unicité exigée et le mode identique réduisent ce risque sans
l'annuler. Et le cas ne se produit qu'à une vraie republication : il ne s'essaie pas sur demande.

**Ce qui manque pour décider : la fréquence.** Personne n'a compté combien d'identifiants changent
d'une publication à l'autre dans ces réseaux. Si c'est rare, demander de reconfigurer suffit. Tracéo
peut le mesurer sans rien servir de plus : à chaque compilation, compter les lignes et les arrêts
dont l'identifiant a disparu alors que le code ou le nom demeure.

**Recommandation.** Ne pas le coder maintenant. Demander à Tracéo ce comptage pendant un mois sur
les réseaux qualifiés ; coder le lot si plus de quelques identifiants changent par mois dans un
réseau, de préférence avec la liste des anciens identifiants.

**20 novembre 2026.** Tracéo fait ce comptage depuis la brique MR1, à chaque compilation : lignes
disparues, stations dont l'identifiant ne désigne plus rien, et celles dont l'identifiant désigne
maintenant une autre station. Pas encore de chiffres : il faut une republication.
