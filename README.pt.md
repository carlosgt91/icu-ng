[English](README.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Українська](README.uk.md) | [Français](README.fr.md) | [Português](README.pt.md)

Plugin ATAK — ICU NG

Transmissão de câmara para o ATAK: vídeo e áudio a partir das câmaras do dispositivo
(traseira, frontal ou USB/UVC) para um servidor de vídeo RTSP, RTSPS ou SRT, com
telemetria KLV MISB ST 0601, anunciada no mapa para que outros utilizadores do
ATAK possam abri-la a partir da sua lista de Vídeo. Um substituto para o TAK ICU
integrado no próprio ATAK.

Manual do utilizador: dentro do ATAK, Definições → Preferências de ferramentas →
Preferências específicas de ferramentas → ICU NG (fonte: docs/user_manual/).

_________________________________________________________________
OBJETIVO E FUNCIONALIDADES

O ICU NG permite a um operador transmitir o que a câmara do dispositivo vê sem
sair do ATAK e sem ter de configurar a transmissão manualmente noutra aplicação
de cada vez. A transmissão tem como nome o indicativo de chamada do operador e
é anunciada através do gestor de ligações de vídeo do ATAK, para que o resto da
equipa possa visualizá-la a partir do ATAK.

- Botão REC no mapa: toque para iniciar/parar com as definições guardadas, pressão
  longa para o painel de definições, pressão longa e arrastar para o deslocar ao
  longo do bordo direito.
- Painel de definições com pré-visualização em direto: câmara, protocolo, URL do
  servidor (introduzido ou lido a partir de um código QR), frase-passe e encriptação
  SRT, qualidade (resolução, fps, bitrate, bitrate adaptativo, microfone).
- MISB ST 0601 KLV sobre RTSP/RTSPS, uma vez por segundo: posição e altitude do
  sensor, azimute e elevação da câmara (sensor de vetor de rotação), campo de
  visão, centro do enquadramento no terreno (dados de elevação do ATAK), indicativo.
- Cone de campo de visão do sensor no marcador próprio durante a transmissão.
- Botão de transmissão no menu radial do marcador próprio.

Transmissão em segundo plano. Um plugin é executado dentro do processo do ATAK, e
o único serviço de primeiro plano do ATAK é do tipo "location", pelo que o Android
interrompe o acesso do plugin à câmara quando o ecrã se desliga. Para continuar a
transmitir com o ecrã desligado, a captura da câmara pode ser efetuada por uma
aplicação complementar opcional, o ICU NG Capture (capture/ no repositório de
código-fonte; uma aplicação Android vulgar, não integrada nesta submissão), que
mantém um serviço de primeiro plano de câmara/microfone. O plugin permanece como
toda a interface de utilizador e controla a aplicação; sem a aplicação instalada,
o plugin transmite autonomamente, apenas em primeiro plano.

_________________________________________________________________
ESTADO

Versão 1.0. Verificado em ATAK-CIV 5.8.0.7 (compilação de programador): emulador
(Android 14) para todas as funcionalidades, incluindo a compilação civRelease;
testado no terreno num tablet Samsung (Android 16) a transmitir por RTSP para
um retransmissor baseado em MediaMTX através de uma VPN. Ainda não executado em
hardware: uma câmara USB (UVC).

Preparado para submissão de terceiros no tak.gov.

_________________________________________________________________
PONTOS DE CONTACTO

Carlos (carlosgt91)
https://github.com/carlosgt91/icu-ng/issues

_________________________________________________________________
PORTAS NECESSÁRIAS

(Isto é importante para ATO, redes e outras questões de segurança)

  Apenas de saída, e apenas enquanto o operador estiver a transmitir, para o
  único servidor de vídeo que o operador configurou (URL introduzido no painel
  ou lido a partir de um QR):

  - RTSP:  TCP para a porta RTSP do servidor (predefinição do MediaMTX 8554).
  - RTSPS: TCP + TLS para a porta RTSPS do servidor (predefinição do MediaMTX 8555).
  - SRT:   UDP para a porta SRT do servidor (predefinição do MediaMTX 8890).

  Sem portas de entrada e sem sockets de escuta. Sem tráfego próprio de ou para
  o servidor TAK: a transmissão é anunciada como uma ligação de vídeo através das
  próprias API do ATAK, transportada no CoT do marcador próprio pelo ATAK. Nada
  é enviado quando não se está a transmitir; sem rede, o plugin simplesmente não
  consegue ligar-se.

  IPC local com a aplicação complementar ICU NG Capture, quando instalada:
  iniciação de atividades (start/stop) e um serviço vinculado AIDL (estado,
  superfície de pré-visualização, pacotes KLV). A aplicação aceita chamadas
  apenas do pacote do ATAK.

_________________________________________________________________
EQUIPAMENTO NECESSÁRIO

  Dispositivo Android suportado pelo ATAK-CIV 5.8, com câmara. Permissões de
  câmara e microfone (do ATAK, ou do ICU NG Capture quando instalado). Sem
  utilização de armazenamento para além das preferências do ATAK.

_________________________________________________________________
EQUIPAMENTO SUPORTADO

  As próprias câmaras do dispositivo. Câmaras USB Video Class (UVC) via USB OTG.
  Um servidor de vídeo que aceite publicação RTSP/RTSPS/SRT, p. ex., MediaMTX ou
  raytheonbbn/tak-video-restreamer.

_________________________________________________________________
COMPILAÇÃO

  Compilação padrão de plugin ATAK. Defina sdk.path em local.properties a apontar
  para um SDK ATAK CIV descompactado, e depois:

      ./gradlew assembleCivDebug
      ./gradlew assembleCivRelease

  ext.ATAK_VERSION em app/build.gradle seleciona a versão do ATAK a visar.
  O projeto tem dois módulos: app (o plugin) e rootencoder-rtsp (ver abaixo).
  No repositório de código-fonte é incluído um terceiro, capture (a aplicação
  complementar), quando presente, o qual não é necessário para compilar o plugin.

_________________________________________________________________
NOTAS PARA DESENVOLVEDORES

  - A transmissão utiliza o RootEncoder 2.8.1 (pedroSG94, Apache-2.0), API
    StreamBase. O diretório rootencoder-rtsp/ é uma cópia do módulo rtsp do
    RootEncoder, modificada para anunciar e enviar uma faixa KLV (RFC 6597,
    application/smpte336m) juntamente com o vídeo e áudio; consulte
    rootencoder-rtsp/NOTICE.md. A aplicação substitui o artefacto rtsp publicado
    por este.
  - Câmaras USB: UVCAndroid 1.0.13 (herohan). O seu código nativo executa
    chamadas de retorno para Java por nome, justificando as suas regras keep do
    proguard.
  - Descodificação QR: ZXing core.
  - As definições residem nas preferências padrão do ATAK (chaves icung.*), pelo
    que o botão REC pode transmitir sem que o painel tenha alguma vez sido aberto.
  - O versionCode é derivado de PLUGIN_VERSION e ATAK_VERSION (não do git), para
    que as compilações criadas a partir deste zip apresentem um versionCode coerente.
  - A interface da aplicação complementar: app/src/main/aidl e CaptureLink.java do
    lado do plugin; os mesmos ficheiros AIDL na aplicação.
