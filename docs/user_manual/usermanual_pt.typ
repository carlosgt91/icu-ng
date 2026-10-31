#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "ICU NG",
   plugin-version: "1.1.6",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
= O que é o ICU NG
#toolbox.side-by-side(columns: (1fr, 8fr))[
#image("plugin_icon.png", width: 90%)
][
O ICU NG transmite a câmara do dispositivo (vídeo e áudio) para um servidor de vídeo por
RTSP, RTSPS ou SRT, com telemetria KLV (MISB ST 0601), e anuncia-a no mapa para
que os restantes utilizadores do ATAK a vejam a partir da sua lista de Video com o seu callsign.

São dois componentes que se instalam em conjunto:
- *Plugin ICU NG* (dentro do ATAK): o botão REC no mapa, o painel de definições e o
  anúncio do vídeo.
- *Aplicação ICU NG Capture*: realiza a transmissão. Continua a transmitir com o ecrã desligado
  ou com outra aplicação aberta, algo que um plugin não consegue fazer por si só.

Sem o ICU NG Capture o plugin transmite ele mesmo, mas o vídeo para ao desligar o
ecrã.
]
]

#tak-slide[
= Instalação
+ Instale o *ICU NG Capture* e depois o plugin *ICU NG*.
+ Abra o ICU NG Capture uma vez a partir da lista de aplicações e aceite as permissões de
  câmara, microfone e notificações. (Caso contrário, serão pedidas na primeira transmissão.)
+ Abra o ATAK e carregue o plugin em Plugins se solicitado.
+ Abra o painel do ICU NG: sob o estado deve constar
  _"Transmissão via ICU NG Capture · continua com o ecrã desligado"_. Se constar
  _"Transmissão via plugin"_, o ATAK não encontrou a aplicação: reinstale-a e reinicie o ATAK.

#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("2.png", width: 95%)
][
  O painel abre-se a partir do menu de ferramentas do ATAK (_ICU NG_) ou mantendo
  premido o botão REC no mapa.
]
]

#tak-slide[
= Transmitir a partir do mapa: o botão REC
#toolbox.side-by-side(columns: (1fr, 1fr, 5fr))[
  #image("1.png", width: 95%)
][
  #image("4b.png", width: 80%)
][
- *Toque*: inicia ou para a transmissão com as definições guardadas, sem abrir o painel.
- *Manter premido e soltar*: abre o painel.
- *Manter premido e arrastar*: desloca o botão para cima ou para baixo ao longo da margem
  direita. A posição é memorizada.

Estados:
- Anel branco: parado.
- Anel âmbar: a ligar (ou a religar).
- Anel vermelho com ponto intermitente: a transmitir.
- Em caso de falha (URL mal introduzido, servidor em baixo, credenciais), surge um aviso com
  o motivo.
]
]

#tak-slide[
= O painel
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("3.png", width: 100%)
][
- *Pré-visualização* da câmara (também durante a transmissão).
- *Estado* e componente emissor (ICU NG Capture ou o plugin).
- *Câmara*: traseira, frontal ou USB. Pode ser alterada durante a transmissão, sem cortes.
- *Protocolo e URL* do servidor: `rtsp://`, `rtsps://` ou `srt://`.
- *QR*: lê o URL do servidor a partir de um código QR.
- *Transmitir / Parar*.
- *KLV*: envia a telemetria com o vídeo. Por baixo: classificação, país (três letras, p. ex. PRT) e ID da missão (se ficar vazio, é o seu callsign com a data UTC, p. ex. ALFA-1-20261031), incluídos em cada pacote.

Tudo é guardado no momento: o botão REC transmite com o que estiver definido no painel.
]
]

#tak-slide[
= Qualidade
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("5.png", width: 100%)
][
O botão *Qualidade* abre:
- *Resolução*: 480p, 720p ou 1080p.
- *FPS*: 15, 24 ou 30.
- *Taxa de bits*: _Auto_ (consoante a resolução) ou fixo, de 0,5 a 6 Mbps.
- *Taxa de bits adaptativa*: se a rede não for suficiente (VPN, dados móveis), reduz a
  qualidade em vez de interromper, e aumenta-a quando melhorar.
- *Microfone*: desativado envia silêncio.

A resolução e os fps aplicam-se ao iniciar a transmissão. A taxa de bits, o modo adaptativo e o microfone
mudam no momento, mesmo em plena transmissão.
]
]

#tak-slide[
= Com o ecrã desligado
#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("6.png", width: 100%)
][
Durante a transmissão, o ICU NG Capture apresenta esta notificação fixa. A transmissão continua:
- com o ecrã desligado ou o telemóvel no bolso,
- com outra aplicação em primeiro plano,
- mesmo se o ATAK for fechado (ao reabri-lo, o plugin retoma-a).

*Parar* na notificação interrompe a transmissão a partir de qualquer local.

Se o servidor for abaixo ou se perder a ligação à rede, a aplicação tenta novamente a cada poucos segundos e
volta a transmitir automaticamente assim que houver ligação.
]
]

#tak-slide[
= O vídeo no mapa e a telemetria KLV
- Durante a transmissão (RTSP ou SRT), os outros utilizadores do ATAK veem o seu vídeo na respetiva
  lista de *Video*, com o seu *callsign* como nome. O RTSPS não é anunciado: o ATAK não
  suporta a abertura deste tipo de ligação.
- No seu marcador surge um *cone* indicando para onde a câmara está a apontar: tão largo
  como o seu campo de visão e tão longo como a distância ao ponto observado. Os
  outros utilizadores do ATAK também o veem. Sem KLV (SRT, câmara USB ou KLV desligado)
  segue a sua direção de deslocação; com a câmara frontal aponta para trás.
- *KLV (MISB ST 0601)*, duas vezes por segundo: a sua posição e altitude, orientação
  da câmara (rumo e inclinação, a partir dos sensores do dispositivo), o seu campo
  de visão, o ponto no terreno para onde está apontada e, quando aponta para
  baixo, os quatro cantos do enquadramento no terreno. Os visualizadores
  compatíveis (o próprio ATAK, entre outros) apresentam estes dados sobre o vídeo
  e no mapa, e podem projetar o vídeo no mapa.
- O KLV é transmitido por RTSP/RTSPS. Por SRT não é enviado.
- Com *câmara USB* não é enviado KLV: não se sabe para onde está apontada.
- *STANAG 4609*: o KLV inclui o conjunto mínimo completo de metadados MISB ST 0902, com a marcação de segurança (MISB ST 0102: classificação e país), o ID da missão e um identificador estável da câmara e do telemóvel (MISB ST 1204), e cada fotograma contém a sua hora UTC (MISB ST 0604), para que os recetores possam associar imagem e telemetria.
]

#tak-slide[
= Câmara USB e configuração por QR
#toolbox.side-by-side(columns: (1fr, 1fr))[
*Câmara USB (UVC)*

Ligue a câmara, selecione _USB_ no painel e transmita. Da primeira vez o Android pede
autorização para que o ICU NG Capture utilize essa câmara. Se for ligada após iniciar,
a imagem surge automaticamente ao encaixar o cabo.
][
*QR do servidor*

O botão *QR* lê o URL do servidor a partir de um código QR (por exemplo, gerado
pelo servidor de vídeo) e preenche o protocolo, o URL e, em SRT, a palavra-passe:

`rtsp://[utilizador:palavrapasse@]host:porta/caminho`

`rtsps://[utilizador:palavrapasse@]host:porta/caminho`

`srt://host:porta?streamid=publish:caminho`

Um QR de visualização (`read:`) também serve: o ICU NG publica sempre.
]
]

#tak-slide[
= Problemas frequentes
- *O painel indica "Transmissão via plugin"*: o ATAK não encontra o ICU NG Capture. Instale-o (ou
  reinstale-o) e reinicie o ATAK.
- *"Não foi possível abrir o ICU NG Capture"*: a aplicação foi desinstalada ou não responde.
  Abra-a uma vez a partir da lista de aplicações.
- *Fica em âmbar*: o servidor não responde. Verifique o URL, a rede ou a VPN.
- *"Erro de autenticação"*: utilizador ou palavra-passe do URL incorretos.
- *"O ICU NG Capture não tem permissão para a câmara"*: Definições do Android → Aplicações → ICU NG Capture →
  Permissões.
- *Sem KLV no recetor*: confirme se a caixa KLV está selecionada, se o
  protocolo é RTSP/RTSPS e se não se trata de uma câmara USB. Sem posição GPS não é enviado.

#toolbox.side-by-side(columns: (3fr, 2fr))[
  #image("7.png", width: 100%)
][
  Este manual: Settings → Tool Preferences → Specific Tool Preferences → ICU NG.
]
]
