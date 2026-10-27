[English](README.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Українська](README.uk.md) | [Français](README.fr.md) | [Português](README.pt.md)

Plugin ATAK — ICU NG

Diffusion de caméra pour ATAK : vidéo et audio depuis les caméras de l'appareil
(arrière, avant ou USB/UVC) vers un serveur vidéo RTSP, RTSPS ou SRT, avec
télémétrie KLV MISB ST 0601, annoncée sur la carte afin que les autres utilisateurs
d'ATAK puissent l'ouvrir depuis leur liste Vidéo. Un remplacement de TAK ICU
intégré au sein d'ATAK.

Manuel de l'utilisateur : dans ATAK, Paramètres → Préférences des outils →
Préférences des outils spécifiques → ICU NG (source : docs/user_manual/).

_________________________________________________________________
OBJET ET FONCTIONNALITÉS

ICU NG permet à un opérateur de diffuser ce que voit la caméra de l'appareil
sans quitter ATAK et sans devoir configurer manuellement le flux dans une autre
application à chaque fois. Le flux porte comme nom l'indicatif de l'opérateur et
est annoncé via le gestionnaire de connexions vidéo d'ATAK, permettant ainsi au
reste de l'équipe de le visionner depuis ATAK.

- Bouton REC sur la carte : appuyer brièvement pour démarrer/arrêter avec les
  paramètres enregistrés, appui long pour le panneau de paramètres, appui long et
  glisser pour le déplacer le long du bord droit.
- Panneau de paramètres avec prévisualisation en direct : caméra, protocole,
  URL du serveur (saisie ou lue depuis un code QR), phrase de passe et chiffrement
  SRT, qualité (résolution, fps, débit, débit adaptatif, microphone).
- KLV MISB ST 0601 sur RTSP/RTSPS, une fois par seconde : position et altitude
  du capteur, azimut et élévation de la caméra (capteur de vecteur de rotation),
  champ de vision, centre du cadre sur le terrain (données d'élévation ATAK),
  indicatif.
- Cône de champ de vision du capteur sur le marqueur propre pendant la diffusion.
- Bouton de diffusion sur le menu radial du marqueur propre.

Diffusion en arrière-plan. Un plugin s'exécute au sein du processus d'ATAK, et
le seul service de premier plan d'ATAK est de type "location", de sorte qu'Android
interrompt l'accès à la caméra du plugin lorsque l'écran s'éteint. Pour continuer
à diffuser avec l'écran éteint, la capture vidéo peut être confiée à une application
compagnon optionnelle, ICU NG Capture (capture/ dans le dépôt de code source ; une
application Android ordinaire, ne faisant pas partie de cette soumission), qui
maintient un service de premier plan caméra/microphone. Le plugin constitue
toujours l'intégralité de l'interface utilisateur et pilote l'application ; sans
l'application installée, le plugin diffuse de manière autonome, uniquement au
premier plan.

_________________________________________________________________
STATUT

Version 1.0. Vérifié sur ATAK-CIV 5.8.0.7 (build développeur) : émulateur
(Android 14) pour chaque fonctionnalité, y compris le build civRelease ; testé sur
le terrain sur une tablette Samsung (Android 16) diffusant en RTSP vers un
retransmetteur basé sur MediaMTX à travers un VPN. Pas encore testé sur le matériel :
une caméra USB (UVC).

Préparé pour la soumission tierce partie sur tak.gov.

_________________________________________________________________
POINTS DE CONTACT

carlosgt91
https://github.com/carlosgt91/icu-ng/issues

_________________________________________________________________
PORTS REQUIS

(Ceci est important pour l'ATO, la gestion du réseau et les aspects de sécurité)

  Sortants uniquement, et uniquement pendant que l'opérateur diffuse, vers
  l'unique serveur vidéo configuré par l'opérateur (URL saisie dans le panneau
  ou lue depuis un QR) :

  - RTSP:  TCP vers le port RTSP du serveur (par défaut 8554 sur MediaMTX).
  - RTSPS: TCP + TLS vers le port RTSPS du serveur (par défaut 8555 sur MediaMTX).
  - SRT:   UDP vers le port SRT du serveur (par défaut 8890 sur MediaMTX).

  Aucun port entrant et aucune socket en écoute. Aucun trafic propre vers ou
  depuis le serveur TAK : le flux est annoncé comme une connexion vidéo via les
  propres API d'ATAK, transporté dans le CoT du marqueur propre par ATAK. Rien
  n'est envoyé en dehors des périodes de diffusion ; en l'absence de réseau, le
  plugin ne peut tout simplement pas se connecter.

  IPC locale avec l'application compagnon ICU NG Capture, lorsqu'elle est
  installée : lancements d'activités (start/stop) et service AIDL lié (état,
  surface de prévisualisation, paquets KLV). L'application n'accepte d'appels
  que de la part du paquet ATAK.

_________________________________________________________________
MATÉRIEL REQUIS

  Appareil Android pris en charge par ATAK-CIV 5.8, doté d'une caméra.
  Autorisations caméra et microphone (d'ATAK, ou d'ICU NG Capture lorsqu'elle est
  installée). Aucune utilisation du stockage au-delà des préférences d'ATAK.

_________________________________________________________________
MATÉRIEL PRIS EN CHARGE

  Les propres caméras de l'appareil. Caméras USB Video Class (UVC) via USB OTG.
  Un serveur vidéo acceptant la publication RTSP/RTSPS/SRT, par ex. MediaMTX ou
  raytheonbbn/tak-video-restreamer.

_________________________________________________________________
COMPILATION

  Compilation standard de plugin ATAK. Définir sdk.path dans local.properties
  vers un SDK ATAK CIV décompressé, puis :

      ./gradlew assembleCivDebug
      ./gradlew assembleCivRelease

  ext.ATAK_VERSION dans app/build.gradle sélectionne la version d'ATAK ciblée.
  Le projet comporte deux modules : app (le plugin) et rootencoder-rtsp (voir
  ci-dessous). Dans le dépôt des sources, un troisième, capture (l'application
  compagnon), est inclus lorsqu'il est présent et n'est pas nécessaire pour
  compiler le plugin.

_________________________________________________________________
NOTES DU DÉVELOPPEUR

  - La diffusion repose sur RootEncoder 2.8.1 (pedroSG94, Apache-2.0), API
    StreamBase. rootencoder-rtsp/ est une copie du module rtsp de RootEncoder,
    modifiée pour annoncer et envoyer une piste KLV (RFC 6597, application/smpte336m)
    aux côtés de la vidéo et de l'audio ; voir rootencoder-rtsp/NOTICE.md.
    L'application le substitue à l'artefact rtsp publié.
  - Caméras USB : UVCAndroid 1.0.13 (herohan). Son code natif rappelle des
    méthodes Java par leur nom, d'où ses règles keep dans proguard.
  - Décodage QR : ZXing core.
  - Les paramètres résident dans les préférences par défaut d'ATAK (clés icung.*),
    de sorte que le bouton REC peut diffuser sans que le panneau n'ait jamais
    été ouvert.
  - versionCode est dérivé de PLUGIN_VERSION et ATAK_VERSION (non de git), de
    sorte que les builds créés à partir de ce zip comportent un versionCode
    cohérent.
  - L'interface de l'application compagnon : app/src/main/aidl et CaptureLink.java
    du côté du plugin ; les mêmes fichiers AIDL dans l'application.
