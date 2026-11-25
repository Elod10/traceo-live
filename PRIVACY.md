# Politique de confidentialité de Tracéo Live

Mise à jour : 7 novembre 2026

Tracéo Live est une application Android libre qui affiche les prochains passages des transports
d'Île-de-France dans des widgets. Cette page décrit les données qu'elle traite, où elles partent,
combien de temps elles sont conservées et comment les effacer.

## L'essentiel

- **Ni compte, ni identifiant d'appareil, ni publicité, ni mesure d'audience.** L'application
  n'embarque aucune bibliothèque tierce.
- **Votre position ne quitte pas le téléphone.** Si vous l'autorisez, elle sert seulement à trouver les
  arrêts proches, dans un fichier stocké sur le téléphone.
- **Seuls les lignes et les arrêts dont vous demandez les passages sont envoyés.** Ils partent vers
  Tracéo ou, si vous avez votre propre clé, vers Île-de-France Mobilités.
- **Aucune donnée n'est vendue, louée ni utilisée à des fins publicitaires.**

## Responsable du traitement

L'éditeur de [Tracéo](https://gotraceo.fr), un particulier agissant à titre non professionnel (voir les
[mentions légales](https://gotraceo.fr/mentions-legales.html)). Contact :
[contact@gotraceo.fr](mailto:contact@gotraceo.fr).

## Ce qui reste sur le téléphone

Ces données sont enregistrées dans l'espace privé de l'application, auquel les autres applications n'ont
pas accès. Elles sont exclues de la sauvegarde Android en ligne, et du transfert vers un nouveau
téléphone : elles ne partent sur aucun compte ni sur aucun autre appareil.

- La configuration de chaque widget (ligne, arrêt, sens, couleur, notification, plage horaire) et les
  derniers passages reçus.
- Vos lignes favorites.
- En mode automatique et pour le widget À proximité :
  - la dernière position lue ;
  - les lieux où vous avez choisi une ligne, pour vous la reproposer au même endroit ;
  - l'index des arrêts d'Île-de-France, téléchargé depuis l'open data d'Île-de-France Mobilités.
- Votre clé API PRIM, si vous en saisissez une, et le nombre de requêtes PRIM du jour.

**Effacement.**
- Supprimer un widget efface sa configuration.
- Pour retirer la clé, videz son champ puis enregistrez.
- Désinstaller l'application, ou vider son stockage dans les paramètres d'Android, efface tout.

## Position

L'autorisation de localisation est facultative. Elle ne sert qu'au mode automatique et au widget
À proximité. Sans elle, tous les autres widgets fonctionnent.

- **Quand elle est lue.** Par défaut, seulement quand vous touchez le widget ou le bouton « Me localiser »
  de sa notification.
- **En arrière-plan.** Seulement si vous activez, widget par widget, le suivi de la position (permission
  « Toujours autoriser »). Le widget ne la lit alors que pendant ses actualisations automatiques, écran
  allumé et dans la plage horaire choisie.
- **Ce qu'elle devient.** L'application la lit par le service de localisation d'Android, puis cherche les
  arrêts proches dans l'index stocké sur le téléphone. La position n'est envoyée à aucun serveur, ni à
  Tracéo ni à Île-de-France Mobilités. Seuls les lignes et les arrêts retenus partent ensuite, comme
  décrit ci-dessous.

## Ce qui quitte le téléphone

Toutes les connexions sont chiffrées (HTTPS). Aucune ne contient d'identifiant d'appareil ni de
position.

### Sans clé PRIM : Tracéo

Le téléphone demande à gotraceo.fr la liste des lignes, les arrêts d'une ligne pendant la configuration,
et les passages d'une ligne à un arrêt.

- **Journaux du serveur web** : adresse IP, date, adresse demandée (donc la ligne et l'arrêt) et nom de
  l'application. Ils sont conservés 14 jours.
- **Journaux de l'application Tracéo** : les requêtes, sans adresse IP. Ils sont limités à 50 Mo et les
  plus anciennes lignes sont effacées au-delà.
- **Île-de-France Mobilités (plateforme PRIM)** : elle ne reçoit aucune requête liée à vos widgets.
  - Tracéo relève à intervalles réguliers les prochains passages de tout le réseau, en une seule requête
    globale. Il répond aux widgets à partir de ce relevé, gardé en mémoire.
  - Les lignes et leurs arrêts viennent d'un référentiel que Tracéo garde en cache et renouvelle de
    lui-même. Si une ligne n'y figure pas encore, le serveur en demande une fois les arrêts à PRIM, sans
    rien qui vous désigne.
  - Île-de-France Mobilités ne voit donc ni votre adresse IP, ni les arrêts que vous suivez.
- **Hébergeur** : mTxServ (MTXSERV SAS), dont le serveur est à Paris chez OVHcloud. Comme tout hébergeur,
  il a un accès technique à la machine.

Le reste du fonctionnement de Tracéo est décrit dans sa
[politique de confidentialité](https://gotraceo.fr/confidentialite.html).

### Avec une clé PRIM : Île-de-France Mobilités

Le téléphone interroge directement PRIM pour les passages, avec votre clé, et l'open data
(data.iledefrance-mobilites.fr) pour les lignes et les arrêts. Île-de-France Mobilités reçoit alors
votre adresse IP, votre clé et les arrêts demandés, selon les
[conditions d'utilisation de PRIM](https://prim.iledefrance-mobilites.fr/fr/conditions-utilisation).
La clé n'est jamais envoyée à Tracéo.

### Index des arrêts

L'index des arrêts ne sert qu'au mode automatique et au widget À proximité : les autres widgets ne le
téléchargent pas. Le téléphone le télécharge sur data.iledefrance-mobilites.fr au premier widget de ce
type. Il le renouvelle ensuite, quand ces widgets lisent la position, chaque semaine en Wi-Fi et chaque
mois sur le réseau mobile. Ce téléchargement est le même pour tout le monde : il ne contient ni position
ni identifiant. Île-de-France Mobilités voit seulement votre adresse IP.

### Notifications et liens

- Les notifications sont créées par le téléphone lui-même. Aucun service de notification en ligne n'est
  utilisé.
- Les liens de l'application (soutien, PRIM, sources des données) s'ouvrent dans votre navigateur. Le site
  ouvert applique alors sa propre politique.

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
