# Politique de confidentialité de Tracéo Live

Mise à jour : 26 novembre 2026, pour la version 3.0

Tracéo Live est une application Android libre qui affiche dans des widgets les prochains passages des
transports d'Île-de-France et d'autres réseaux de France. Cette page décrit les données qu'elle traite,
où elles partent, combien de temps elles sont conservées et comment les effacer.

## L'essentiel

- **Ni compte, ni identifiant d'appareil, ni publicité, ni mesure d'audience.** L'application
  n'embarque aucune bibliothèque tierce.
- **Votre position ne quitte pas le téléphone.** Si vous l'autorisez, elle sert seulement à reconnaître
  votre ville et à trouver les arrêts proches, à partir de fichiers stockés sur le téléphone.
- **Seuls le réseau, les lignes et les arrêts dont vous demandez les passages sont envoyés.** Ils partent
  vers Tracéo ou, en Île-de-France et si vous avez votre propre clé, vers Île-de-France Mobilités.
- **Aucune donnée n'est vendue, louée ni utilisée à des fins publicitaires.**

## Responsable du traitement

L'éditeur de [Tracéo](https://gotraceo.fr), un particulier agissant à titre non professionnel (voir les
[mentions légales](https://gotraceo.fr/mentions-legales.html)). Contact :
[contact@gotraceo.fr](mailto:contact@gotraceo.fr).

## Ce qui reste sur le téléphone

Ces données sont enregistrées dans l'espace privé de l'application, auquel les autres applications n'ont
pas accès. Elles sont exclues de la sauvegarde Android en ligne, et du transfert vers un nouveau
téléphone : elles ne partent sur aucun compte ni sur aucun autre appareil.

- La configuration de chaque widget (réseau, ligne, arrêt, sens, couleur, notification, plage horaire)
  et les derniers passages reçus.
- Vos lignes favorites et le dernier réseau choisi.
- Le catalogue des réseaux servis par Tracéo : leur nom, leur zone, leurs modes, leur source et leur
  licence. Il est le même pour tout le monde.
- En mode automatique et pour le widget À proximité :
  - la dernière position lue ;
  - les lieux où vous avez choisi une ligne, pour vous la reproposer au même endroit.
- L'index des arrêts de chaque réseau où il a servi (voir « Index des arrêts » plus bas).
- Votre clé API PRIM, si vous en saisissez une, et le nombre de requêtes PRIM du jour.

**Effacement.**
- Supprimer un widget efface sa configuration.
- L'index d'un réseau hors Île-de-France s'efface de lui-même au bout de 30 jours sans servir, si aucun
  widget n'y est posé.
- Pour retirer la clé, videz son champ puis enregistrez.
- Désinstaller l'application, ou vider son stockage dans les paramètres d'Android, efface tout.

## Position

L'autorisation de localisation est facultative. Sans elle, vous choisissez votre réseau dans la liste,
et tous les widgets fonctionnent, sauf le mode automatique et le widget À proximité.

- **Pour reconnaître votre ville.** À la première étape de la configuration, « Trouver mon réseau »
  demande la position approximative, qui suffit. Si l'accès à la position est déjà accordé, cette étape
  la lit d'elle-même pour cocher d'avance le réseau de l'endroit. La position est comparée, sur le
  téléphone, à la zone de chaque réseau du catalogue. Elle n'est pas enregistrée : elle est oubliée à
  la fermeture de la configuration.
- **Pour le mode automatique et le widget À proximité**, qui demandent la position exacte :
  - **Quand elle est lue.** Par défaut, seulement quand vous touchez le widget ou le bouton
    « Me localiser » de sa notification.
  - **En arrière-plan.** Seulement si vous activez, widget par widget, le suivi de la position
    (permission « Toujours autoriser »). Le widget ne la lit alors que pendant ses actualisations
    automatiques, écran allumé et dans la plage horaire choisie.
  - **Ce qu'elle devient.** L'application la lit par le service de localisation d'Android, reconnaît le
    réseau de l'endroit dans le catalogue, puis cherche les arrêts proches dans l'index de ce réseau,
    stocké sur le téléphone.
- **Dans tous les cas**, la position n'est envoyée à aucun serveur : ni à Tracéo, ni à Île-de-France
  Mobilités, ni à un réseau de transport. Seuls partent ensuite, comme décrit ci-dessous, le réseau, les
  lignes et les arrêts retenus. Si l'index d'un réseau manque au téléphone, il est téléchargé chez
  Tracéo : ce téléchargement est le même pour tout le monde, mais il indique à Tracéo le réseau, donc la
  ville, comme le ferait une demande de passages.

## Ce qui quitte le téléphone

Toutes les connexions sont chiffrées (HTTPS). Aucune ne contient d'identifiant d'appareil ni de
position.

### Tracéo

Tracéo est la seule source des réseaux autres que l'Île-de-France. En Île-de-France, il sert les widgets
qui n'ont pas de clé PRIM.

Le téléphone demande à gotraceo.fr :

- **le catalogue des réseaux**, à l'étape « Réseau » de la configuration, une fois par jour écran allumé
  si un widget d'un autre réseau que l'Île-de-France est posé, et quand le mode automatique ne reconnaît
  pas l'endroit avec le catalogue qu'il a déjà ;
- **les lignes d'un réseau et les arrêts d'une ligne**, pendant la configuration ;
- **l'index des arrêts d'un réseau**, pour le mode automatique et le widget À proximité, ou pour
  distinguer deux arrêts de même nom pendant la configuration ;
- **les passages d'une ligne à un arrêt**.

Tracéo en garde :

- **Journaux du serveur web** : adresse IP, date, adresse demandée (donc le réseau, la ligne et l'arrêt)
  et nom de l'application. Ils sont conservés 14 jours.
- **Journaux de l'application Tracéo** : les requêtes, sans adresse IP. Ils sont limités en taille, et les
  plus anciennes lignes sont effacées au-delà.

Les producteurs de données ne reçoivent aucune requête liée à vos widgets :

- **Île-de-France Mobilités (plateforme PRIM)**, pour les widgets sans clé :
  - Tracéo relève à intervalles réguliers les prochains passages de tout le réseau, en une seule requête
    globale. Il répond aux widgets à partir de ce relevé, gardé en mémoire.
  - Les lignes et leurs arrêts viennent d'un référentiel que Tracéo garde en cache et renouvelle de
    lui-même. Si une ligne n'y figure pas encore, le serveur en demande une fois les arrêts à PRIM, sans
    rien qui vous désigne.
  - Île-de-France Mobilités ne voit donc ni votre adresse IP, ni les arrêts que vous suivez.
- **Les autres réseaux** : Tracéo relève lui-même les horaires et le temps réel qu'ils publient en données
  ouvertes, parfois par le point d'accès national transport.data.gouv.fr, et ne leur transmet rien de vos
  requêtes. Ils ne voient ni votre adresse IP, ni les arrêts que vous suivez.

**Hébergeur** : mTxServ (MTXSERV SAS), dont le serveur est à Paris chez OVHcloud. Comme tout hébergeur,
il a un accès technique à la machine.

Le reste du fonctionnement de Tracéo est décrit dans sa
[politique de confidentialité](https://gotraceo.fr/confidentialite.html).

### Avec une clé PRIM : Île-de-France Mobilités

La clé ne sert qu'aux widgets d'Île-de-France. Le téléphone interroge alors directement PRIM pour les
passages, avec votre clé, et l'open data (data.iledefrance-mobilites.fr) pour les lignes et les arrêts.
Île-de-France Mobilités reçoit votre adresse IP, votre clé et les arrêts demandés, selon les
[conditions d'utilisation de PRIM](https://prim.iledefrance-mobilites.fr/fr/conditions-utilisation).
La clé n'est jamais envoyée à Tracéo.

### Index des arrêts

L'index des arrêts sert au mode automatique et au widget À proximité. Les autres widgets ne le
téléchargent pas, sauf hors Île-de-France, quand deux arrêts d'une ligne portent le même nom : seul
l'index les distingue.

- **Île-de-France** : le téléphone le télécharge sur data.iledefrance-mobilites.fr au premier widget de ce
  type. Il le renouvelle ensuite, quand ces widgets lisent la position, chaque semaine en Wi-Fi et chaque
  mois sur le réseau mobile. Île-de-France Mobilités voit seulement votre adresse IP.
- **Autres réseaux** : le téléphone télécharge chez Tracéo l'index du réseau de l'endroit, la première fois
  qu'il en a besoin. Il le renouvelle quand le catalogue annonce de nouveaux horaires pour ce réseau.

Ces téléchargements sont les mêmes pour tout le monde : ils ne contiennent ni position ni identifiant.

### Notifications et liens

- Les notifications sont créées par le téléphone lui-même. Aucun service de notification en ligne n'est
  utilisé.
- Les liens de l'application (soutien, PRIM, sources et licences de chaque réseau) s'ouvrent dans votre
  navigateur. Le site ouvert applique alors sa propre politique.

## Vos droits

Vous disposez d'un droit d'accès, de rectification, d'effacement, d'opposition et de limitation.

- Les données du téléphone sont entre vos mains : voir « Effacement » plus haut.
- Côté Tracéo, aucune donnée n'est rattachée à une personne ou à un appareil. Les adresses IP des
  journaux s'effacent d'elles-mêmes au bout de 14 jours.
- Pour toute question : [contact@gotraceo.fr](mailto:contact@gotraceo.fr), réponse sous un mois.
- Réclamation : auprès de la CNIL, [www.cnil.fr](https://www.cnil.fr).

## Code source

Le code de l'application est public : chacun peut vérifier ce qui précède dans
[ce dépôt](https://github.com/Elod10/traceo-live).

## Modifications

Toute modification est publiée sur cette page, avec sa date de mise à jour. L'historique des versions
reste consultable dans le dépôt.
