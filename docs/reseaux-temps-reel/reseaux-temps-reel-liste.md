# Réseaux de transport avec temps réel : liste par type d'accès

_Relevé du 20 novembre 2026. France (catalogue national transport.data.gouv.fr, chaque flux testé), complétée par les réseaux hors catalogue, la Belgique, le Luxembourg, la Suisse et Monaco. 284 entrées._

**Légende techno** : GTFS-RT TU = TripUpdates (horaires prévus en temps réel), VP = VehiclePositions (positions des véhicules), SA = ServiceAlerts (perturbations). SIRI = SOAP/XML (SM = StopMonitoring, ET = EstimatedTimetable, VM = VehicleMonitoring, GM/SX = messages et perturbations). SIRI Lite = SIRI en JSON sur HTTP GET.

## Synthèse

| Type d'accès | Nombre |
|---|---|
| Ouvert, sans clé | 221 |
| Ouvert, SIRI avec requestor_ref public (aucune inscription) | 13 |
| Clé ou compte à créer en ligne (inscription en libre-service) | 7 |
| Demande à adresser au gestionnaire (mail ou formulaire) | 8 |
| Temps réel existant mais non publié : à demander | 30 |
| Flux déclaré mais injoignable ou refusé lors du test | 5 |

⚠ « alertes seulement » / « positions seulement » : le flux ouvert ne donne pas de prochains passages (pas de TripUpdates ni de SIRI SM/ET).

Les démarches (mail, formulaire, inscription) et les messages rédigés sont dans le fichier `reseaux-temps-reel-integration.md`.

## Ouvert, sans clé (221)

### GTFS-RT (191)

| Ville / territoire | Réseau | Type | Techno |
|---|---|---|---|
| France | Renfe AVE (France–Espagne) | longue distance / national | GTFS-RT (TU, VP) |
| France | Eurostar | longue distance / national | GTFS-RT (SA, TU) |
| France | FlixBus / FlixTrain | longue distance / national | GTFS-RT (TU) |
| France | Trenitalia France | longue distance / national | GTFS-RT (TU) |
| Sud — Provence-Alpes-Côte d’Azur | ZOU ! TER Sud | train régional | GTFS-RT (SA, TU) |
| Sud — Provence-Alpes-Côte d’Azur | ZOU ! TER Intermétropoles (Transdev) | train régional | GTFS-RT (TU) |
| Normandie (agrégat régional) | Agrégat Normandie (Atoumod) | agrégat régional | GTFS-RT (TU, VP) |
| Bayonne / Pays basque | Txik Txak | interurbain | GTFS-RT (SA, TU) |
| Bretagne | BreizhGo Car | interurbain | GTFS-RT (TU) |
| Centre-Val de Loire | Rémi (cars Centre-Val de Loire) | interurbain | GTFS-RT (SA, TU, VP) |
| Corse | Cars interurbains de Corse | interurbain | GTFS-RT (TU) |
| Haut-Rhin | Fluo Grand Est 68 | interurbain | GTFS-RT (SA, TU, VP) |
| Isère | Cars Région Isère | interurbain | GTFS-RT (SA, TU) |
| La Réunion | Car Jaune | interurbain | GTFS-RT (SA, TU, VP) |
| Occitanie | liO (cars Région Occitanie) | interurbain | GTFS-RT (TU, VP) |
| Pays de la Loire | Aléop | interurbain | GTFS-RT (SA, TU, VP) |
| Sud — Provence-Alpes-Côte d’Azur | ZOU ! Proximité (cars) | interurbain | GTFS-RT (SA, TU) |
| Sud — Provence-Alpes-Côte d’Azur | ZOU ! Express (cars) | interurbain | GTFS-RT (SA, TU) |
| Sud — Provence-Alpes-Côte d’Azur | ZOU ! scolaire | interurbain | GTFS-RT (SA) | ⚠ alertes seulement
| Agde | Cap’Bus | urbain | GTFS-RT (TU, VP) |
| Agen | Tempo Bus | urbain | GTFS-RT (TU, VP) |
| Aix-les-Bains | Ondéa | urbain | GTFS-RT (SA, TU) |
| Alès | Alesy | urbain | GTFS-RT (TU, VP) |
| Amiens | Ametis | urbain | GTFS-RT (SA, TU, VP) |
| Angoulême | Möbius | urbain | GTFS-RT (TU) |
| Annemasse | TAC Mobilité | urbain | GTFS-RT (SA, TU) |
| Arles | Envia | urbain | GTFS-RT (TU, VP) |
| Arras | Artis | urbain | GTFS-RT (TU, VP) |
| Aubenas | Tout'enbus | urbain | GTFS-RT (SA, TU, VP) |
| Auch | Alliance | urbain | GTFS-RT (SA, TU, VP) |
| Auray | Glazgo | urbain | GTFS-RT (SA, TU, VP) |
| Auxerre | Léo | urbain | GTFS-RT (SA, TU, VP) |
| Avignon | Orizo | urbain | GTFS-RT (TU, VP) |
| Bagnols-sur-Cèze | UGGO | urbain | GTFS-RT (TU) |
| Bar-le-Duc | TUB | urbain | GTFS-RT (TU) |
| Barentin (Caux-Austreberthe) | MOCA | urbain | GTFS-RT (SA, TU, VP) |
| Beaune | Côte&Bus | urbain | GTFS-RT (TU, VP) |
| Bernay | l'Bus | urbain | GTFS-RT (SA, TU, VP) |
| Bolbec – Lillebonne | REZO'BUS | urbain | GTFS-RT (SA, TU, VP) |
| Bourg-en-Bresse | Rubis | urbain | GTFS-RT (TU, VP) |
| Bourges | AggloBus | urbain | GTFS-RT (SA, TU) |
| Bourgoin-Jallieu (CAPI) | Ruban | urbain | GTFS-RT (TU, VP) |
| Béziers | beeMob | urbain | GTFS-RT (TU, VP) |
| Calais | SITAC | urbain | GTFS-RT (SA, TU, VP) |
| Carcassonne | RTCA | urbain | GTFS-RT (SA, TU, VP) |
| Cavaillon | Cmonbus | urbain | GTFS-RT (SA, TU, VP) |
| Cayenne (Guyane) | Agglobus | urbain | GTFS-RT (TU, VP) |
| Challans | Yolé | urbain | GTFS-RT (SA, TU, VP) |
| Chambéry | Synchro Bus | urbain | GTFS-RT (SA, TU) |
| Chamonix-Mont-Blanc | Chamonix Bus | urbain | GTFS-RT (SA, TU, VP) |
| Charleville-Mézières | TAC | urbain | GTFS-RT (TU) |
| Cherbourg-en-Cotentin | Cap Cotentin | urbain | GTFS-RT (TU, VP) |
| Chinon | CVL Mobilité | urbain | GTFS-RT (TU, VP) |
| Cholet | Choletbus | urbain | GTFS-RT (SA, TU) |
| Châlons-en-Champagne | SITAC | urbain | GTFS-RT (TU) |
| Châtellerault | TAC | urbain | GTFS-RT (TU) |
| Clermont-Ferrand | T2C | urbain | GTFS-RT (TU) |
| Cosne-Cours-sur-Loire | Papillon | urbain | GTFS-RT (SA, TU, VP) |
| Cœur du Var | OZ'CAR | urbain | GTFS-RT (TU, VP) |
| Dieppe | DeepMob | urbain | GTFS-RT (TU, VP) |
| Dijon | DiviaMobilités | urbain | GTFS-RT (TU, VP) |
| Dole | Grandole Mobilités | urbain | GTFS-RT (TU, VP) |
| Draguignan | TEDbus | urbain | GTFS-RT (SA, TU, VP) |
| Dreux | Linead | urbain | GTFS-RT (TU, VP) |
| Figeac | Le Bus (Figeac) | urbain | GTFS-RT (TU, VP) |
| Flers | Nemus | urbain | GTFS-RT (TU, VP) |
| Forbach | Forbus | urbain | GTFS-RT (TU, VP) |
| Fougères | SURF | urbain | GTFS-RT (TU, VP) |
| Fréjus – Saint-Raphaël | Le Bus | urbain | GTFS-RT (SA, TU, VP) |
| Fécamp | Ficibus | urbain | GTFS-RT (TU, VP) |
| Gap | L'Agglo en Bus | urbain | GTFS-RT (TU, VP) |
| Granville | Néva | urbain | GTFS-RT (SA, TU, VP) |
| Guingamp – Paimpol | Guingamp-Paimpol Mobilité | urbain | GTFS-RT (TU, VP) |
| Guérande – La Baule | Lila Presqu'ile | urbain | GTFS-RT (SA, TU, VP) |
| Haguenau | Ritmo | urbain | GTFS-RT (TU, VP) |
| Hazebrouck | Hop Bus | urbain | GTFS-RT (SA, TU, VP) |
| Honfleur | Hobus | urbain | GTFS-RT (SA, TU, VP) |
| Issoire | Cherpa | urbain | GTFS-RT (SA, TU, VP) |
| L'Argentière-la-Bessée (Écrins) | Navettes Pays des Écrins | urbain | GTFS-RT (TU, VP) |
| La Croix-Valmer | La Croix-Valmer (navette) | urbain | GTFS-RT |
| La Roche-sur-Yon | Impulsyon | urbain | GTFS-RT (SA) | ⚠ alertes seulement
| Landerneau | Ar bus | urbain | GTFS-RT (TU) |
| Lannion | Tilt | urbain | GTFS-RT (TU) |
| Lavaur | LISATIS | urbain | GTFS-RT (SA, TU, VP) |
| Le Luc – Le Cannet-des-Maures | Le TACO | urbain | GTFS-RT (TU, VP) |
| Le Mans | SETRAM | urbain | GTFS-RT (SA, TU, VP) |
| Le Tampon (La Réunion) | CarSud | urbain | GTFS-RT (TU, VP) |
| Lens – Liévin – Béthune | TADAO | urbain | GTFS-RT (TU, VP) |
| Lisieux | Astrobus | urbain | GTFS-RT (TU, VP) |
| Loire Forez | Cars Région Loire (lignes 19 et 32) | urbain | GTFS-RT (TU, VP) |
| Longwy | TGL | urbain | GTFS-RT (TU, VP) |
| Lorient | Izilo | urbain | GTFS-RT (SA, TU) |
| Louviers – Val-de-Reuil | SEMO | urbain | GTFS-RT (SA, TU, VP) |
| Lunel | Olé | urbain | GTFS-RT (TU, VP) |
| Lunéville | Lunéo | urbain | GTFS-RT (SA, TU, VP) |
| Maizières-lès-Metz (Rives de Moselle) | Riv'Connect | urbain | GTFS-RT (TU) |
| Mauguio – Pays de l'Or | Transp’Or | urbain | GTFS-RT (TU, VP) |
| Mayenne | May'bus | urbain | GTFS-RT (SA, TU, VP) |
| Mende | TUM | urbain | GTFS-RT (TU, VP) |
| Menton | Zest | urbain | GTFS-RT (TU, VP) |
| Metz | Le Met' | urbain | GTFS-RT (SA, TU, VP) |
| Miribel | Colibri | urbain | GTFS-RT (TU) |
| Montluçon | Maelis | urbain | GTFS-RT (TU, VP) |
| Montpellier | TaM | urbain | GTFS-RT (SA, TU, VP) |
| Montélimar | Montelibus | urbain | GTFS-RT (TU, VP) |
| Moulins | Aléo | urbain | GTFS-RT (TU, VP) |
| Mulhouse | Solea | urbain | GTFS-RT (SA, TU) |
| Nancy | Stan | urbain | GTFS-RT (TU) |
| Nancy (périurbain) | SUB (Sub-urbain Nancy) | urbain | GTFS-RT (TU) |
| Narbonne | Citibus | urbain | GTFS-RT (SA, TU, VP) |
| Nogent-le-Rotrou | Nosbus | urbain | GTFS-RT (SA, TU, VP) |
| Nîmes | Tango | urbain | GTFS-RT (SA, TU, VP) |
| Obernai | Pass'O | urbain | GTFS-RT (TU) |
| Orange | TCVO | urbain | GTFS-RT (TU, VP) |
| Orléans | TAO | urbain | GTFS-RT (TU) |
| Parthenay | Pybus | urbain | GTFS-RT (SA) |
| Perpignan | Sankéo | urbain | GTFS-RT (SA, TU, VP) |
| Poitiers | Vitalis | urbain | GTFS-RT (TU) |
| Pont-à-Mousson | Le Bus | urbain | GTFS-RT (TU) |
| Pontivy | PondiBUS | urbain | GTFS-RT (SA, TU, VP) |
| Porto-Vecchio | A Citadina | urbain | GTFS-RT (SA, TU, VP) |
| Quimper | Qub | urbain | GTFS-RT (SA, TU, VP) |
| Quimperlé | TBK | urbain | GTFS-RT (SA, TU) |
| Reims | Grand Reims Mobilités | urbain | GTFS-RT (TU) |
| Rennes | STAR | urbain | GTFS-RT (SA, TU, VP) |
| Riom | RLV Mobilités | urbain | GTFS-RT (TU, VP) |
| Roanne | Star | urbain | GTFS-RT (TU) |
| Rosny-sous-Bois | Titus | urbain | GTFS-RT (SA, TU, VP) |
| Rouen | Astuce | urbain | GTFS-RT (SA, TU, VP) |
| Rumilly | Jybus | urbain | GTFS-RT (SA, TU, VP) |
| Sablé-sur-Sarthe | Réso | urbain | GTFS-RT (SA, TU, VP) |
| Saint-Avold | Transavold | urbain | GTFS-RT (TU) |
| Saint-Brevin-les-Pins | Brévibus | urbain | GTFS-RT (TU, VP) |
| Saint-Dié-des-Vosges | Sylvia | urbain | GTFS-RT (SA, TU) |
| Saint-Louis (68) | Distribus | urbain | GTFS-RT (TU) |
| Saint-Maixent-l'École | Fil Bus | urbain | GTFS-RT (SA, TU, VP) |
| Saint-Malo | MAT | urbain | GTFS-RT (TU, VP) |
| Saint-Omer | Mouvéo | urbain | GTFS-RT (TU, VP) |
| Saint-Pierre (La Réunion) | Alternéo | urbain | GTFS-RT (SA, TU, VP) |
| Saint-Sulpice-la-Pointe | Saint-Sulpice-la-Pointe (navette) | urbain | GTFS-RT (TU, VP) |
| Saint-Étienne | STAS | urbain | GTFS-RT (TU) |
| Saintes | Buss | urbain | GTFS-RT (TU, VP) |
| Sarlat-la-Canéda | Sarlat Bus | urbain | GTFS-RT (TU, VP) |
| Sarthe (périurbain du Mans) | Illygo | urbain | GTFS-RT (SA, TU, VP) |
| Sud Martinique | SudLib (CAESM) | urbain | GTFS-RT (SA, TU, VP) |
| Sète | Sète Agglopole Mobilité | urbain | GTFS-RT (SA, TU, VP) |
| Sélestat | ELSA | urbain | GTFS-RT (TU) |
| Tarbes – Lourdes | TLP | urbain | GTFS-RT (TU, VP) |
| Thionville | TeMo'b | urbain | GTFS-RT (TU) |
| Toul | Colibri | urbain | GTFS-RT (TU) |
| Toulouse | Tisséo | urbain | GTFS-RT (SA, TU) |
| Tours | Fil Bleu | urbain | GTFS-RT (SA, TU, VP) |
| Troyes | TCAT | urbain | GTFS-RT (SA, TU, VP) |
| Trévoux (Dombes Saône Vallée) | Saônibus | urbain | GTFS-RT |
| Valence – Romans | Citéa | urbain | GTFS-RT (SA, TU, VP) |
| Vannes | Kicéo | urbain | GTFS-RT (SA, TU, VP) |
| Vendôme | Move | urbain | GTFS-RT (SA, TU, VP) |
| Verdun | Rezo | urbain | GTFS-RT (TU) |
| Vernon | SNgo! | urbain | GTFS-RT (TU, VP) |
| Vesoul | moova | urbain | GTFS-RT (TU, VP) |
| Vichy | Mobivie | urbain | GTFS-RT (TU, VP) |
| Vico (Corse-du-Sud) | U Vagabondu | urbain | GTFS-RT (SA) |
| Vienne | L'va | urbain | GTFS-RT (SA, TU, VP) |
| Vierzon | Le Vib' | urbain | GTFS-RT (SA, TU, VP) |
| Vitré | Vitobus | urbain | GTFS-RT (TU, VP) |
| Yvetot | Vikibus | urbain | GTFS-RT (SA, TU, VP) |
| Épernay | Mouvéo | urbain | GTFS-RT (TU) |
| Épinal | Imagine | urbain | GTFS-RT (TU) |
| Évian | EVA'D | urbain | GTFS-RT (TU) |
| Évreux | Transurbain | urbain | GTFS-RT (SA, TU, VP) |
| Île de Ré | RespiRé | urbain | GTFS-RT (TU, VP) |
| Flers (scolaire) | Némus scolaire | scolaire | GTFS-RT (TU, VP) |
| Bagnères-de-Bigorre – La Mongie | Navettes Bagnères – La Mongie | navette / saisonnier | GTFS-RT (TU) |
| Bordeaux – Mérignac | Navette aéroport de Bordeaux (30′Direct) | navette / saisonnier | GTFS-RT (SA, TU, VP) |
| Cavalaire-sur-Mer | Cavalaire (été) | navette / saisonnier | GTFS-RT |
| Cavalaire-sur-Mer | Cavalaire (hiver) | navette / saisonnier | GTFS-RT |
| Chinon | CVL Mobilité navette estivale | navette / saisonnier | GTFS-RT |
| Cogolin | Navette saisonnière Cogolin | navette / saisonnier | GTFS-RT |
| Grimaud | Navette Grimaud | navette / saisonnier | GTFS-RT |
| Huez | Navettes Alpe d'Huez | navette / saisonnier | GTFS-RT (TU) |
| Ile de Noirmoutier | Gratibus (Noirmoutier) | navette / saisonnier | GTFS-RT (SA) |
| Isère | Transaltitude (navettes stations) | navette / saisonnier | GTFS-RT (TU) |
| Les Avanchers-Valmorel | Valmobus (Valmorel) | navette / saisonnier | GTFS-RT (TU) |
| Oloron-Sainte-Marie | La Navette (Oloron) | navette / saisonnier | GTFS-RT (SA, TU, VP) |
| Ramatuelle | Navette Ramatuelle | navette / saisonnier | GTFS-RT |
| Rayol-Canadel-sur-Mer | Navette Rayol-Canadel | navette / saisonnier | GTFS-RT |
| Saint-Nicolas-de-Port | La Navette (Sel et Vermois) | navette / saisonnier | GTFS-RT (TU, VP) |
| Saint-Tropez | Navette Saint-Tropez | navette / saisonnier | GTFS-RT |
| Tignes | Navettes Tignes | navette / saisonnier | GTFS-RT (TU, VP) |
| Val-d'Isère | Navettes Val d'Isère | navette / saisonnier | GTFS-RT (SA) |
| Vernon – Giverny | SNgo! navette Giverny | navette / saisonnier | GTFS-RT |

### GTFS-RT + SIRI (29)

| Ville / territoire | Réseau | Type | Techno |
|---|---|---|---|
| France | SNCF (TGV INOUI, OUIGO, Intercités, TER) | longue distance / national | GTFS-RT (SA, TU) · SIRI Lite (ET, SX) |
| Beauvais (interurbain) | Corolis interurbain + TAD | interurbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Compiègne (interurbain) | TIC interurbain | interurbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Angers | Irigo | urbain | GTFS-RT (SA, TU, VP) · SIRI |
| Beauvais | Corolis | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Blois | Azalys | urbain | GTFS-RT (TU, VP) · SIRI |
| Bordeaux | TBM | urbain | GTFS-RT (SA, TU, VP) · SIRI Lite (ET, GM, SM) |
| Boulogne-sur-Mer | Marinéo | urbain | GTFS-RT (SA, TU) · SIRI |
| Brest | Bibus | urbain | GTFS-RT (SA, TU, VP) · SIRI |
| Cannes | Palmbus | urbain | GTFS-RT (SA, TU, VP) · SIRI |
| Chantilly | Aire’MOB | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Chartres | Filibus | urbain | GTFS-RT (TU, VP) · SIRI |
| Clermont (Oise) | Le Bus | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Compiègne | TIC | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Creil | AXO | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Crépy-en-Valois | Mobi | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Dunkerque | DK'BUS | urbain | GTFS-RT (TU, VP) · SIRI |
| Estrées-Saint-Denis | Hoplà | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Le Havre | LiA | urbain | GTFS-RT (SA, TU, VP) · SIRI |
| Liancourt | Libellule | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Lille | ilévia | urbain | GTFS-RT (TU) · SIRI (SM) |
| Méru (CC des Sablons) | Sablons Bus | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Méru (CC Thelloise) | PassThelle Bus | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Nantes | Naolib | urbain | GTFS-RT (SA, TU) · SIRI · SIRI Lite |
| Nice | Lignes d'Azur | urbain | GTFS-RT (TU, VP) · SIRI |
| Noyon | Lib'Bus | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Pont-Sainte-Maxence | TOHM | urbain | GTFS-RT (TU, VP) · SIRI (SM, VM) |
| Toulon | Mistral | urbain | GTFS-RT (SA, TU, VP) · SIRI |
| Valenciennes | Transvilles | urbain | GTFS-RT (TU, VP) · SIRI |

### SIRI / SIRI Lite (1)

| Ville / territoire | Réseau | Type | Techno |
|---|---|---|---|
| France | BlaBlaCar Bus | longue distance / national | SIRI Lite (ET) |

## Ouvert, SIRI avec requestor_ref public (aucune inscription) (13)

### GTFS-RT + SIRI (2)

| Ville / territoire | Réseau | Type | Techno |
|---|---|---|---|
| La Rochelle | Yélo | urbain | GTFS-RT · SIRI |
| Marseille – Aix-en-Provence | RTM + réseaux de la Métropole AMP (La Métropole Mobilité) | urbain | GTFS-RT (SA) · SIRI |

### SIRI / SIRI Lite (11)

| Ville / territoire | Réseau | Type | Techno |
|---|---|---|---|
| Pays de la Loire (agrégat régional) | Agrégat Pays de la Loire (AOM + Aléop) | agrégat régional | SIRI |
| Dordogne | Cars Région Dordogne (Nouvelle-Aquitaine) | interurbain | SIRI |
| Bressuire | Tréma | urbain | SIRI |
| Caen | Twisto | urbain | SIRI |
| Langon (Sud Gironde) | Sud Gironde Mobilités | urbain | SIRI |
| Libourne | Calibus | urbain | SIRI |
| Marmande | Evalys | urbain | SIRI |
| Niort | Tanlib | urbain | SIRI |
| Rochefort | R'Bus | urbain | SIRI |
| Thouars | T'Bus | urbain | SIRI |
| Tulle | TuT | urbain | SIRI |

## Clé ou compte à créer en ligne (inscription en libre-service) (7)

### France

#### SIRI / SIRI Lite (3)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Île-de-France | Île-de-France Mobilités (RATP, SNCF Transilien, Optile…) | régional (IDF) | SIRI Lite (ET, GM, SM) | Inscription en ligne (prim.iledefrance-mobilites.fr) |
| Lyon | TCL | urbain | SIRI Lite (ET, SX, VM) | Inscription en ligne (data.grandlyon.com) |
| Pau | Idelis | urbain | SIRI | Inscription en ligne (api.idelis.fr) |

### Belgique

#### GTFS-RT + SIRI (2)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Flandre (Anvers, Gand, Louvain…) | De Lijn | interurbain | GTFS-RT (SA, TU, VP) · SIRI (ET, SX) | Inscription en ligne (data.delijn.be) |
| Wallonie (Charleroi, Liège-Verviers, Namur, Mons, Brabant wallon, Luxembourg) | TEC | interurbain | GTFS-RT (SA, TU, VP) · SIRI (ET, SX, VM) | Mail (inscription + opendata@belgianmobilitycompany.be) |

#### Autre (HAFAS, OJP, API propriétaire…) (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Bruxelles | STIB-MIVB | urbain | REST JSON | Mail (inscription + opendata@belgianmobilitycompany.be) |

### Suisse

#### GTFS-RT + SIRI (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Suisse (Genève, Lausanne, Bâle, Zurich, Berne…) | opentransportdata.swiss (CFF/SBB, TPG, TL, BVB, VBZ, Bernmobil, CarPostal…) | réseau national | GTFS-RT (SA, TU) · OJP · SIRI (ET, SX (imprévus)) | Inscription en ligne (api-manager.opentransportdata.swiss) |

## Demande à adresser au gestionnaire (mail ou formulaire) (8)

### France

#### GTFS-RT (2)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Ouest de La Réunion (Le Port, Saint-Paul…) | Kar'Ouest | urbain | GTFS-RT (TU, VP) | Formulaire (formulaire karouest.re) |
| Sophia Antipolis – Antibes | Envibus | urbain | GTFS-RT (TU, VP) | Mail (opendata@agglo-casa.fr) |

#### GTFS-RT + SIRI (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Besançon | Ginko | urbain | GTFS-RT (SA, TU) · SIRI | Mail (ginko.support-ssi@keolis.com) |

#### SIRI / SIRI Lite (3)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Montbéliard | évolitY | urbain | SIRI | Mail (contact.evolity@moventis.com) |
| Saint-Brieuc | TUB | urbain | SIRI | Mail (contact@tub.bzh) |
| Strasbourg | CTS | urbain | SIRI Lite (ET, GM, SM) | Mail (open-data@cts-strasbourg.fr) |

### Belgique

#### GTFS-RT + SIRI (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Belgique (trains) | SNCB / NMBS | longue distance / national | GTFS-RT (SA, TU) · REST JSON · SIRI (ET, SX) | Mail (gtfs@sncb.be) |

### Luxembourg

#### Autre (HAFAS, OJP, API propriétaire…) (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Luxembourg (tout le pays) | mobiliteit.lu (RGTR, AVL, CFL, TICE, Luxtram) | réseau national | HAFAS REST | Mail (opendata-api@atp.etat.lu) |

## Temps réel existant mais non publié : à demander (30)

### France

#### Pas de flux public (29)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Normandie (cars régionaux) | Nomad Car | interurbain |  | — |
| Ajaccio | Muvistrada | urbain |  | Formulaire (opendata.ca-ajaccien.corsica) |
| Annecy | Sibra | urbain |  | Mail (sibra@sibra.fr) |
| Annonay | CoqueliGO | urbain |  | Mail (agence@coqueligo.fr) |
| Arcachon (Bassin sud) | Baïa | urbain |  | Mail (contact.baia@transdev.com) + Mail (contact@nouvelle-aquitaine-mobilites.fr) |
| Bassin d'Arcachon nord | Alégo | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) |
| Bastia | Via Bastia | urbain |  | Mail (viabastia@orange.fr) |
| Belfort | Optymo | urbain |  | Mail (contact@optymo.fr) |
| Bergerac | TUB | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) + Formulaire (la-cab.fr) |
| Brive-la-Gaillarde | Libéo | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) + Formulaire (libeo-brive.fr) |
| Chalon-sur-Saône | Zoom | urbain |  | — |
| Châteauroux | Horizon | urbain |  | Mail (contact@bus-horizon.com) |
| Cognac | Transcom | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) |
| Colmar | Trace | urbain |  | Mail (info@trace-colmar.fr) |
| Douai | Évéole | urbain |  | Formulaire (eveole.com) |
| Grasse | Sillages | urbain |  | Mail (contact.sillages@moventis.com) |
| Laval | TUL | urbain |  | Formulaire (tul-laval.com) |
| Le Creusot – Montceau-les-Mines | monRéZO | urbain |  | — |
| Les Sables-d'Olonne | Oléane | urbain |  | Formulaire (oleane-mobilites.fr) |
| Limoges | TCL Limoges Métropole | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) + Formulaire (stcl.fr) |
| Maubeuge | Stibus | urbain |  | — |
| Mont-de-Marsan | Movima | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) |
| Morlaix | Linéotim | urbain |  | Formulaire (lineotim.com) |
| Nevers | Tanéo | urbain |  | — |
| Pointe-à-Pitre – Les Abymes (Guadeloupe) | Karu'lis | urbain |  | Mail (karulis@stepgroup.gp) |
| Rodez | Agglobus | urbain |  | Formulaire (agglobus-rodez.fr) |
| Royan | Cara'bus | urbain |  | Mail (contact@nouvelle-aquitaine-mobilites.fr) + Formulaire (cara-bus.com) |
| Saint-Nazaire | Ycéo | urbain |  | Formulaire (yceo-mobilite.fr) |
| Thonon-les-Bains | STAR't | urbain |  | Mail (contact@star-t.fr) |

### Monaco

#### Pas de flux public (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Monaco | Compagnie des Autobus de Monaco (CAM) | urbain |  | Mail (s.massiani@cam.mc (contact du jeu GTFS)) |

## Flux déclaré mais injoignable ou refusé lors du test (5)

### GTFS-RT (4)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Fort-de-France (Martinique) | Mozaïk (CACEM) | urbain | GTFS-RT (SA, TU, VP) | — |
| Grenoble | TAG | urbain | GTFS-RT (TU) | Mail (contact@mobilites-m.fr) |
| Lamballe | Distribus | urbain | GTFS-RT (TU, VP) | — |
| Nord Martinique | Réseau Nord (Cap Nord) | urbain | GTFS-RT (SA, TU, VP) | — |

### SIRI / SIRI Lite (1)

| Ville / territoire | Réseau | Type | Techno | Démarche |
|---|---|---|---|---|
| Cluses | ARVI | urbain | SIRI | — |
