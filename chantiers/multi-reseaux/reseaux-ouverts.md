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

## 11. Questions à Tracéo

Ce qui manque à l'appli dans le contrat, et qu'elle ne contourne qu'en attendant une réponse.

| | Question | Ce que fait l'appli en attendant |
|---|---|---|
| Q1 | Un code stable dans le corps des 404 (par exemple `code` : `network`, `line`, `stop`, `route`), à côté du message | Elle reconnaît le message en toutes lettres ; reformulé, il redevient un 404 sans précision |
| Q2 | Un délai relatif à côté de `nextAt` (comme `ageSec` l'est pour `fetchedAt`), pour ne pas dépendre de l'horloge du téléphone | Elle compare `nextAt` à sa propre horloge |
| Q4 | Dans la réponse des passages, un indicateur explicite de réveil (par exemple `waking` : vrai), au lieu de le déduire de `fresh` et de `nextAt` | Elle prend pour un réveil toute réponse sans relevé récent dont le `nextAt` est à moins de 20 secondes, et borne ses retours à deux |
| Q5 | `textColor` nul quand le réseau ne publie pas de couleur de texte, même s'il publie la couleur de la ligne | Hors Île-de-France, elle ne tient pas compte d'un texte noir |
| Q6 | Quand Tracéo regroupera les quais d'un arrêt, l'arrêt regroupé aura-t-il un identifiant neuf, ou celui d'un des quais ? | Un widget posé sur un quai garde son identifiant : neuf, il demandera à être reconfiguré ; repris d'un quai, il recevra les deux sens sans rien dire |
| Q3 | Dans la réponse des arrêts d'une ligne, de quoi situer un arrêt : ses coordonnées (`lat`, `lon`), ou son rang sur la ligne | Pour distinguer deux arrêts de même nom, elle télécharge l'index entier du réseau |
