<div align="center">

# Plugin ATAK — ICU NG (Next Generation)

[![ATAK-CIV](https://img.shields.io/badge/ATAK--CIV-5.8%2B-blue.svg)](https://tak.gov)
[![Padrões](https://img.shields.io/badge/Padrões-MISB%20ST%200601%20%7C%20ST%200902%20%7C%20STANAG%204609-success.svg)](https://gwg.nga.mil/)
[![Protocolos](https://img.shields.io/badge/Protocolos-RTSP%20%7C%20RTSPS%20%7C%20SRT-orange.svg)](#segurança-e-requisitos-ato)
[![Versão](https://img.shields.io/badge/Versão-v1.1.9-brightgreen.svg)](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9)
[![Licença](https://img.shields.io/badge/Licença-Apache%202.0-lightgrey.svg)](LICENSE)

**Plugin profissional de transmissão de vídeo tático de baixa latência para ATAK-CIV.**  
*Transmissão nativa de câmara com metadados STANAG 4609 / MISB KLV, cone de sensor (FOV) dinâmico no mapa e captura em segundo plano.*

---

[English](README.md) • [Español](README.es.md) • [Deutsch](README.de.md) • [Українська](README.uk.md) • [Français](README.fr.md) • [Português](README.pt.md)

</div>

---

## 📌 Visão Geral

O **ICU NG** é um substituto moderno e direto para a ferramenta legada *TAK ICU*, integrado diretamente no ATAK como um plugin de primeira linha. Permite que um operador em campo transmita vídeo e áudio em tempo real das câmaras do dispositivo (traseira/frontal) ou de fontes USB externas (UVC) para servidores de vídeo **RTSP, RTSPS (TLS) ou SRT**.

As transmissões são identificadas com o indicativo (*callsign*) do operador e anunciadas automaticamente através do gestor nativo de ligações de vídeo do ATAK (`<__video>` CoT). A área visível da câmara é desenhada dinamicamente nos mapas dos membros da equipa, com projeção em tempo real do cone de visão do sensor e dos cantos do enquadramento no terreno.

```
       [ Câmara do Dispositivo / USB UVC ]
                       │
                       ▼
           ┌───────────────────────┐
           │     Plugin ICU NG     │ ◄─── Telemetria (Vetor de rotação, GPS, terreno)
           │ (ou App Acompanhante) │ ───► Injeção KLV MISB ST 0601 / ST 0902 (2 Hz)
           └───────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼ (RTSP / RTSPS / SRT)        ▼ (Anúncio CoT Vídeo)
    ┌───────────────────────┐   ┌───────────────────────┐
    │ MediaMTX / Restreamer │   │      TAK Server       │
    └───────────────────────┘   └───────────────────────┘
                │                           │
                └──────────────┬────────────┘
                               ▼
                [ Clientes ATAK Remotos / TOC ]
              (Transmissões com projeção no mapa)
```

---

## 🚀 Transferências (Versão Atual: v1.1.9)

Os ficheiros APK pré-compilados estão disponíveis em [GitHub Releases](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9):

| Pacote | Ficheiro | Descrição |
| :--- | :--- | :--- |
| **Plugin ATAK** | [ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk) | Pacote de plugin nativo para ATAK-CIV 5.8+ |
| **App Acompanhante** | [ICU-NG-Capture-1.1.9-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ICU-NG-Capture-1.1.9-release.apk) | Serviço independente para transmissão com ecrã desligado |

### Checksums SHA-256

```text
540a5438499391b80c161145b147224888935620cce2320d7ffa525dca632ef3  ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk
214f14cf95a484510dad0727bd7a3c6e602ffb329b4899334cb07e1748a770e8  ICU-NG-Capture-1.1.9-release.apk
```

---

## 🎯 Capacidades Principais

### 1. Interface Tática Sem Fricção
* **Botão REC no mapa:** Toque para iniciar/parar com as predefinições guardadas. Pressão longa para abrir o painel de configurações rápidas; arraste para mover ao longo da margem do ecrã.
* **Integração no Menu Radial:** Inicie e monitorize transmissões diretamente a partir do menu radial do próprio marcador.
* **Configuração por Código QR:** Leitura instantânea de URLs de servidor, credenciais e chaves de encriptação SRT através da câmara.

### 2. Conformidade OTAN / STANAG 4609 e KLV MISB
* **MISB ST 0601 e ST 0902:** Gera pacotes de metadados KLV conformes a **2 Hz** (conforme ST 1402):
  * Posição da plataforma e altitude elipsoidal (Tags 13, 14, 15).
  * Azimute, elevação e rotação relativa da câmara (Tags 5, 6, 7, 20).
  * Campo de visão horizontal (Tag 16) e largura do alvo (Tag 22).
  * Centro do enquadramento projetado e coordenadas dos cantos no solo (Tags 23–26).
  * Conjunto Local de Segurança (MISB ST 0102, Tag 48) e ID de Missão padrão (Tag 3).
  * Identificador MIIS Core (MISB ST 1204, Tag 94) com UUID v5 estável para rastreio de sensor/plataforma.
* **Marcas Temporais de Precisão MISB ST 0604:** Injetadas em cabeçalhos SEI em cada fotograma H.264/H.265.
* **Projeção de Vídeo no Terreno:** Os recetores podem projetar a perspetiva visual da câmara diretamente sobre a cartografia de elevação do ATAK.

### 3. Cone de Campo de Visão (FOV) Dinâmico
* Ao contrário de plugins antigos que desenham cones estáticos baseados no rumo GPS, o ICU NG calcula o **vetor de orientação real da câmara**.
* Injeta a geometria `<sensor>` em tempo real no Cursor on Target (`REPORT_LOCATION`), exibindo a área exata coberta no solo.

### 4. Transmissão em Segundo Plano com Ecrã Bloqueado
* No Android, o acesso à câmara em segundo plano é revogado assim que o ecrã se desliga.
* O ICU NG integra-se com o **ICU NG Capture**, uma aplicação acompanhante (`capture/`) com serviço em primeiro plano `camera|microphone`.
* Mantenha a transmissão ativa com o ecrã bloqueado para poupar bateria e manter a disciplina operacional.

### 5. Multiprotocolo e Codificação de Alto Desempenho
* Protocolos suportados: **RTSP** (RFC 2326), **RTSPS** (encriptação TLS) e **SRT** (Secure Reliable Transport com encriptação AES e palavra-passe).
* Códecs: **H.264 (AVC) Main Profile** e **H.265 (HEVC)**.
* Relógio de taxa de fotogramas constante (CFR) e taxa de bits adaptativa configurável.
* Suporte total para lentes integradas e câmaras externas **USB UVC / OTG**.

---

## 🔒 Segurança e Requisitos ATO (Authority to Operate)

Concebido para as exigências rigorosas de redes militares e governamentais:

| Protocolo | Porta Padrão | Direção | Encriptação / Transporte | Finalidade |
| :--- | :--- | :--- | :--- | :--- |
| **RTSP** | `8554` | Apenas de saída | TCP sem encriptação | Ingestão clássica de vídeo |
| **RTSPS** | `8555` | Apenas de saída | TLS sobre TCP | Transmissão segura governamental |
| **SRT** | `8890` | Apenas de saída | UDP / AES-128/256 | Transporte resiliente de baixa latência |

* **Zero portas de entrada:** Sem sockets à escuta nem superfícies de ataque de rede no terminal móvel (EUD).
* **Isolamento Total (Air-Gapped):** Sem dependências de nuvem, sem telemetria externa, sem ligações de rede não autorizadas.
* **Segurança IPC Local:** Comunicação entre o plugin e a app acompanhante restrita à assinatura de pacotes via AIDL.

---

## 🛠️ Compilação e Desenvolvimento

### Requisitos
* Java 17 (OpenJDK / Temurin)
* Android SDK (API 34–36, Build Tools 34.0.0+)
* SDK do ATAK-CIV (5.8.0.7+)

### Compilação
1. Defina o caminho do SDK em `local.properties`:
   ```properties
   sdk.path=/caminho/para/ATAK-CIV-5.8.0.7-SDK
   ```
2. Compile os APKs de depuração ou lançamento:
   ```bash
   ./gradlew assembleCivDebug
   ./gradlew assembleCivRelease
   ```
   *(Para compilar a app acompanhante: `./gradlew :capture:assembleRelease`)*.

---

## 📄 Licença e Atribuições

* Publicado sob a **[Licença Apache 2.0](LICENSE)**.
* Motor de streaming baseado no [RootEncoder](https://github.com/pedroSG94/RootEncoder) (Apache-2.0) com injeção de faixa KLV RFC 6597 (`rootencoder-rtsp/`).
* Suporte de câmara USB via [UVCAndroid](https://github.com/herohan/UVCAndroid) (Apache-2.0).
* Descodificação de códigos QR via [ZXing](https://github.com/zxing/zxing) (Apache-2.0).

---

<div align="center">
  <sub>Mantido por <b><a href="https://github.com/carlosgt91">carlosgt91</a></b> • Verificado para o ecossistema ATAK-CIV</sub>
</div>
