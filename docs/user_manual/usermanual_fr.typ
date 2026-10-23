#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "ICU NG",
   plugin-version: "1.0",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
= Qu'est-ce qu'ICU NG
#toolbox.side-by-side(columns: (1fr, 8fr))[
#image("plugin_icon.png", width: 90%)
][
ICU NG diffuse la caméra de l'appareil (vidéo et audio) vers un serveur vidéo via
RTSP, RTSPS ou SRT, avec télémétrie KLV (MISB ST 0601), et l'annonce sur la carte
afin que les autres utilisateurs d'ATAK la voient depuis leur liste Video avec votre callsign.

Il se compose de deux éléments installés ensemble :
- *Plugin ICU NG* (dans ATAK) : le bouton REC sur la carte, le panneau de configuration et
  l'annonce vidéo.
- *Application ICU NG Capture* : effectue la diffusion. Elle continue de diffuser avec l'écran éteint
  ou lorsqu'une autre application est au premier plan, ce qu'un plugin ne peut pas faire seul.

Sans ICU NG Capture, le plugin diffuse lui-même, mais la vidéo s'arrête dès que l'écran
s'éteint.
]
]

#tak-slide[
= Installation
+ Installez *ICU NG Capture*, puis le plugin *ICU NG*.
+ Ouvrez ICU NG Capture une première fois depuis le tiroir d'applications et acceptez les autorisations
  pour la caméra, le microphone et les notifications. (Sinon, elles vous seront demandées lors de la première diffusion.)
+ Ouvrez ATAK et chargez le plugin dans Plugins si cela vous est demandé.
+ Ouvrez le panneau ICU NG : sous l'état, il doit être indiqué
  _"Diffusion via ICU NG Capture · continue avec l'écran éteint"_. S'il est indiqué
  _"Diffusion via le plugin"_, ATAK n'a pas détecté l'application : réinstallez-la et redémarrez ATAK.

#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("2.png", width: 95%)
][
  Le panneau s'ouvre depuis le menu des outils d'ATAK (_ICU NG_) ou en maintenant
  le bouton REC enfoncé sur la carte.
]
]

#tak-slide[
= Diffuser depuis la carte : le bouton REC
#toolbox.side-by-side(columns: (1fr, 1fr, 5fr))[
  #image("1.png", width: 95%)
][
  #image("4b.png", width: 80%)
][
- *Appui simple* : démarre ou arrête la diffusion avec les paramètres enregistrés, sans ouvrir le panneau.
- *Appui long et relâchement* : ouvre le panneau.
- *Appui long et glissement* : déplace le bouton vers le haut ou le bas le long du bord
  droit. La position est mémorisée.

États :
- Anneau blanc : arrêté.
- Anneau ambre : connexion (ou reconnexion) en cours.
- Anneau rouge avec point clignotant : en cours de diffusion.
- En cas d'échec (URL mal orthographiée, serveur inaccessible, identifiants), un avis en indique
  la cause.
]
]

#tak-slide[
= Le panneau
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("3.png", width: 100%)
][
- *Aperçu* de la caméra (également pendant la diffusion).
- *État* et qui diffuse (ICU NG Capture ou le plugin).
- *Caméra* : arrière, avant ou USB. Peut être changée en cours de diffusion, sans coupure.
- *Protocole et URL* du serveur : `rtsp://`, `rtsps://` ou `srt://`.
- *QR* : lit l'URL du serveur à partir d'un code QR.
- *Diffuser / Arrêter*.
- *KLV* : envoie la télémétrie avec la vidéo.

Tout est enregistré instantanément : le bouton REC diffuse avec les paramètres affichés sur le panneau.
]
]

#tak-slide[
= Qualité
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("5.png", width: 100%)
][
Le bouton *Qualité* ouvre :
- *Résolution* : 480p, 720p ou 1080p.
- *FPS* : 15, 24 ou 30.
- *Bitrate* : _Auto_ (selon la résolution) ou fixe, de 0,5 à 6 Mbps.
- *Bitrate adaptatif* : si le réseau est limité (VPN, données mobiles), il réduit la
  qualité au lieu de couper, et l'augmente dès qu'il s'améliore.
- *Microphone* : désactivé, il transmet du silence.

La résolution et les fps s'appliquent au début de la diffusion. Le bitrate, le mode adaptatif et le microphone
changent instantanément, même en pleine diffusion.
]
]

#tak-slide[
= Avec l'écran éteint
#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("6.png", width: 100%)
][
Pendant la diffusion, ICU NG Capture affiche cette notification persistante. La diffusion continue :
- avec l'écran éteint ou l'appareil dans la poche,
- lorsqu'une autre application est au premier plan,
- même si ATAK est fermé (à sa réouverture, le plugin reprend la diffusion).

*Arrêter* dans la notification interrompt la diffusion depuis n'importe où.

Si le serveur tombe ou si la connexion réseau est perdue, l'application réessaie toutes les quelques secondes et
reprend automatiquement la diffusion dès le rétablissement de la connexion.
]
]

#tak-slide[
= La vidéo sur la carte et la télémétrie KLV
- Pendant la diffusion (RTSP ou SRT), les autres utilisateurs d'ATAK voient votre vidéo dans leur
  liste *Video*, avec votre *callsign* comme nom. RTSPS n'est pas annoncé : ATAK ne
  sait pas ouvrir ce type de lien.
- Un *cône* apparaît sur votre marqueur indiquant la direction visée par la caméra. Avec la caméra
  avant, il pointe vers l'arrière.
- *KLV (MISB ST 0601)*, une fois par seconde : votre position et altitude, direction
  de la caméra (cap et inclinaison, issus des capteurs de l'appareil), son champ
  de vision et le point au sol visé. Les visionneuses compatibles (notamment le propre
  ATAK) l'affichent en surimpression sur la vidéo et sur la carte.
- Le KLV passe par RTSP/RTSPS. Par SRT, il n'est pas envoyé.
- Avec une *caméra USB*, le KLV n'est pas envoyé : on ne sait pas vers où elle pointe.
]

#tak-slide[
= Caméra USB et configuration par QR
#toolbox.side-by-side(columns: (1fr, 1fr))[
*Caméra USB (UVC)*

Branchez la caméra, choisissez _USB_ dans le panneau et diffusez. La première fois, Android demande
l'autorisation pour qu'ICU NG Capture utilise cette caméra. Si elle est connectée après avoir commencé,
l'image apparaît d'elle-même dès le branchement.
][
*QR du serveur*

Le bouton *QR* lit l'URL du serveur à partir d'un code QR (par exemple, généré
par le serveur vidéo) et renseigne le protocole, l'URL et, pour SRT, la passphrase :

`rtsp://[utilisateur:motdepasse@]host:port/chemin`

`rtsps://[utilisateur:motdepasse@]host:port/chemin`

`srt://host:port?streamid=publish:chemin`

Un QR de visionnage (`read:`) convient aussi : ICU NG publie toujours.
]
]

#tak-slide[
= Problèmes fréquents
- *Le panneau indique "Diffusion via le plugin"*: ATAK ne détecte pas ICU NG Capture. Installez-la (ou
  réinstallez-la) et redémarrez ATAK.
- *"Impossible d'ouvrir ICU NG Capture"*: l'application a été désinstallée ou ne répond pas.
  Ouvrez-la une fois depuis le tiroir d'applications.
- *Reste ambre*: le serveur ne répond pas. Vérifiez l'URL, le réseau ou le VPN.
- *"Erreur d'authentification"*: nom d'utilisateur ou mot de passe incorrect dans l'URL.
- *"ICU NG Capture n'a pas l'autorisation pour l'appareil photo"*: Paramètres Android → Applications → ICU NG Capture →
  Autorisations.
- *Pas de KLV sur le récepteur*: vérifiez que la case KLV est cochée, que le
  protocole est RTSP/RTSPS et qu'il ne s'agit pas d'une caméra USB. Sans position GPS, rien n'est envoyé.

#toolbox.side-by-side(columns: (3fr, 2fr))[
  #image("7.png", width: 100%)
][
  Ce manuel : Settings → Tool Preferences → Specific Tool Preferences → ICU NG.
]
]
