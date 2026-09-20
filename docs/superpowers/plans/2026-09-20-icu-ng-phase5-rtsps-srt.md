# ICU NG — Fase 5: RTSPS/SRT (cifrado) + selector de protocolo + feedback visual — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ICU NG hoy solo emite RTSP en claro (`RtspCamera2` a pelo, sin
selector de protocolo, sin feedback de estado real — los 6 callbacks de
`ConnectChecker` son stubs vacíos desde la Fase 1). Esta fase añade **RTSPS**
(TLS) y **SRT** (cifrado AES por passphrase) usando RootEncoder — ya
integrado como dependencia completa desde la Fase 1 (`library:2.8.1`, que
incluye los módulos `rtsp`, `srt`, `rtmp`, `udp`, `whip` vía `api project(...)`,
verificado), más la UI para elegir protocolo/cifrado y ver el estado real
(conectando/emitiendo/error). Ver
[2026-09-17-icu-ng-design.md](../specs/2026-09-17-icu-ng-design.md), sección
"Fuera de alcance de v1" ("RTSPS/RTMP/SRT/multicast UDP,
autenticación/cifrado simultáneos").

**Architecture:** Todo el trabajo es en `IcuNgPlugin.java` + recursos de UI
(`main_layout.xml`, `strings.xml`, `arrays.xml` nuevo). `SelfVideoDetailHandler.java`
y `SelfSensorFovController.java` de la Fase 3 **no se tocan** — el cono
`<sensor>` sigue dependiendo solo de `isStreamingWithFix()`
(protocolo-agnóstico), y `SelfVideoDetailHandler` sigue funcionando sin
cambios porque, para RTSPS, este plugin simplemente nunca construye un
`ConnectionEntry` (ver Decisión 6) — su propio guard `if (entry == null)
return false;` ya hace el resto.

Todas las decisiones de esta sección están verificadas contra el código
fuente real de RootEncoder (rama de la etiqueta `2.8.1`, la misma que fija
`build.gradle`) y, donde toca, contra el jar del SDK de ATAK vía `javap` — no
de memoria.

### Decisión 1: un solo campo `Camera2Base camera`, no `RtspCamera2`

Verificado leyendo el fichero completo `library/src/main/java/com/pedro/library/base/Camera2Base.java`:
`RtspCamera2` y `SrtCamera2` (`library/.../rtsp/RtspCamera2.kt` y
`.../srt/SrtCamera2.kt`) son **estructuralmente idénticas** — ambas extienden
`Camera2Base`, ambas tienen los mismos dos constructores
`(OpenGlView, ConnectChecker)` / `(Context, ConnectChecker)`, y la parte
pública que ya usa este plugin (`prepareVideo()`, `prepareAudio()`,
`startPreview(Facing)`, `stopPreview()`, `startStream(String)`,
`stopStream()`, `isStreaming()`, `isOnPreview()`, `replaceView(Context)`,
`replaceView(OpenGlView)`) está **toda declarada en `Camera2Base`**, no en
las subclases — las subclases solo implementan los métodos `*Imp` internos
(`startStreamImp`, `getVideoDataImp`, etc.) que delegan a su cliente interno
(`RtspClient`/`SrtClient`).

Consecuencia directa: cambiar el campo de `RtspCamera2 camera` a
`Camera2Base camera` es la única modificación estructural necesaria para que
**todo el ciclo de vida ya resuelto en las Fases 3 y 4**
(`tearDownPane()`, `stopCameraAndStream()`, `stopStreamOnly()`, el
`SurfaceHolder.Callback` con el gotcha de `surfaceCreated()`, el
"segundo plano" con `replaceView(Context)`) siga funcionando **sin ningún
cambio de lógica**, sea cual sea el protocolo. Solo cambia *qué constructor
concreto* se llama la única vez que se construye una cámara nueva, dentro de
`showPane()`.

### Decisión 2: qué protocolo usa qué clase — y qué significa la URL

Verificado en `rtsp/src/main/java/com/pedro/rtsp/rtsp/RtspClient.kt`: RTSP y
RTSPS son **la misma clase** (`RtspCamera2` → `RtspClient`). El TLS se activa
solo por el esquema de la URL:

```kotlin
val urlParser = UrlParser.parse(url, arrayOf("rtsp", "rtsps"))
...
tlsEnabled = urlParser.scheme.endsWith("s")
```

SRT, en cambio, es una **clase totalmente distinta** (`SrtCamera2` →
`SrtClient`, módulo `srt` separado) — no hay forma de "activar SRT" sobre un
`RtspCamera2` existente, hay que construir un `SrtCamera2`.

Por eso el selector de protocolo (`Spinner`, 3 entradas: RTSP/RTSPS/SRT) hace
dos cosas distintas según el caso:
- Alternar entre **RTSP ↔ RTSPS**: no requiere reconstruir la cámara (misma
  clase) — solo reescribe el esquema de la URL (`rtsp://` ↔ `rtsps://`) y,
  para RTSPS, qué certificado usar (Decisión 4).
- Alternar hacia/desde **SRT**: requiere una clase distinta. Ver Decisión 3
  para cuándo se permite.

### Decisión 3: el límite SRT solo se puede cruzar sin cámara viva

Dado que `camera` normalmente ya existe en cuanto se abre el panel (el
preview arranca solo, antes de pulsar "Emitir" — Fase 1), bloquear el
selector "mientras `camera != null`" dejaría el selector inútil desde el
primer frame. En vez de eso, el límite real es **solo la clase concreta**:

- Cambiar entre RTSP y RTSPS es libre en cualquier momento (misma clase,
  Decisión 2) — incluso con `camera` ya construida o emitiendo.
- Cambiar hacia/desde SRT con una `camera` ya construida (`camera != null`)
  se **rechaza**: se revierte la selección del spinner a la última válida y
  se muestra un mensaje de estado. Para pasar a/desde SRT hay que cerrar el
  panel del todo (lo que ya libera `camera` vía `tearDownPane()`, si no se
  está emitiendo en segundo plano) y reabrirlo.

Esto evita tener que reconstruir `camera` en caliente mientras hay preview u
emisión activos — algo que **no** está resuelto por el ciclo de vida de las
Fases 3/4 (que solo contempla "construir una vez, reutilizar en segundo
plano, destruir al cerrar del todo") y que se sale claramente del alcance de
esta fase.

### Decisión 4: certificados RTSPS — por defecto estricto, opt-in permisivo

Verificado leyendo `RtspStreamClient.kt`, `RtspClient.kt` y
`common/.../socket/java/TcpStreamSocketJava.kt` (la ruta real de creación del
socket TLS):

```kotlin
// TcpStreamSocketJava.onConnectSocket(...)
val context = SSLContext.getInstance("TLS")
context.init(null, certificates?.let { arrayOf(it) }, SecureRandom())
```

`certificates` (un `TrustManager?`) es `null` por defecto. Pasar `null` a
`SSLContext.init(keyManagers, trustManagers, ...)` significa **usar los
`TrustManager` por defecto de la plataforma** (validación normal contra CAs
del sistema) — no "confiar en todo". Para un certificado autofirmado (el
caso realista de un receptor RTSPS en LAN, como MediaMTX en el propio
puesto), la conexión **fallará** salvo que se le pase un `TrustManager`
propio vía `RtspStreamClient.addCertificates(TrustManager?)`.

Decisión: exponer un único checkbox, "Confiar en cualquier certificado (solo
pruebas/LAN)", que cuando está marcado instala un `X509TrustManager`
permisivo (acepta cualquier cadena). Etiquetado explícitamente como
inseguro/solo-pruebas en la propia UI. No se expone
`setTlsHostVerification(Boolean)` como control aparte — se deja en su valor
por defecto de la librería (`false`, verificado en `RtspClient.kt`:
`var tlsHostVerification = false`), que ya es el comportamiento permisivo
adecuado para servidores LAN identificados por IP en vez de hostname; añadir
un segundo control para esto es complejidad extra sin un caso de uso claro
en v1 — fuera de alcance deliberado.

### Decisión 5: passphrase SRT — el comentario de la librería es engañoso, verificado con el código real

Verificado en `srt/src/main/java/com/pedro/srt/srt/SrtClient.kt`:

```kotlin
/**
 * Set passphrase for encrypt. Use empty value to disable it.
 */
fun setPassphrase(passphrase: String, type: EncryptionType) {
  if (!isStreaming) {
    if (passphrase.length !in 10..79) {
      throw IllegalArgumentException("passphrase must between 10 and 79 length")
    }
    commandsManager.setPassphrase(passphrase, type)
  }
}
```

El comentario ("Use empty value to disable it") es **engañoso para este
método** — con un passphrase vacío (longitud 0, fuera de `10..79`) esta
misma función **lanza `IllegalArgumentException`** en vez de desactivar el
cifrado. (La clase interna `CommandsManager.setPassphrase` sí acepta vacío
para desactivar — pero `SrtClient`/`SrtStreamClient`, que es lo único
expuesto a este plugin, nunca llega a llamarla si la longitud no cuadra.)

Consecuencia: la forma correcta de "sin cifrar" en SRT es **no llamar a
`setPassphrase()` en absoluto** (por defecto, `encryptor` es `null` —
verificado en `CommandsManager.kt` — sin cifrado). Este plugin valida en
cliente, antes de llamar a la API real, exactamente la misma regla
(`10..79`) para evitar la excepción: si el campo de passphrase está vacío,
no se llama a `setPassphrase()`; si no está vacío pero mide menos de 10 o
más de 79 caracteres, se rechaza el arranque con un mensaje de estado en vez
de dejar que la librería lance la excepción.

`EncryptionType` (verificado en
`srt/.../packets/control/handshake/EncryptionType.kt`) tiene valores
`NONE`, `AES128`, `AES192`, `AES256` — el selector de tipo de cifrado solo
ofrece los tres AES (si no hay passphrase, ya no se llama a `setPassphrase`
en absoluto, así que `NONE` como opción de UI sería un estado redundante que
no aporta nada).

### Decisión 6: anuncio CoT/`IVideoConnectionManager` — RTSP y SRT sí, RTSPS no (limitación real del SDK)

Verificado con `javap` contra `main.jar` del SDK de ATAK real:

```
$ javap -classpath /opt/atak-sdk/main.jar 'gov.tak.api.video.ConnectionEntryBase$Protocol'
public final class ... extends java.lang.Enum<...> {
  RAW; UDP; RTSP; RTMP; RTMPS; HTTP; HTTPS; FILE; TCP; RTP; DIRECTORY; SRT;
  ...
}
```

**No existe una constante `RTSPS`.** Y `javap -v` (constant pool) confirma
que `Protocol.RTSP.toURL()` siempre construye literalmente `"rtsp" + "://"`
— no hay forma de que ese enum represente una URL `rtsps://`. Usar
`Protocol.RTSP` para anunciar un stream que en realidad es RTSPS
anunciaría a otros ATAK una URL rota (`rtsp://` sin TLS) que su cliente de
Vídeo no podría abrir contra un servidor que exige TLS.

Decisión: para RTSPS, este plugin **no construye `ConnectionEntry`** ni lo
registra en `IVideoConnectionManager` — el push RTSPS al servidor sigue
funcionando exactamente igual, solo que no se anuncia por CoT ni aparece en
la herramienta de Vídeo nativa de otros ATAK. Con `connectionEntry == null`,
`SelfVideoDetailHandler.toCotDetail()` (Fase 3, sin tocar) ya devuelve
`false` por su propio guard existente (`if (entry == null) return false;`) —
cero cambios necesarios en ese fichero. El cono `<sensor>` (indicador de
dirección, no expone la URL) se sigue mostrando igual para los tres
protocolos, porque `SelfSensorFovController` no mira `connectionEntry` en
absoluto.

Para SRT sí existe `Protocol.SRT` — se anuncia con normalidad. También se
verificó (`javap` sobre `ConnectionEntry`) que existe
`setPassphrase(String)`/`getPassphrase()` en el propio `ConnectionEntry`, y
que el `Builder` tiene `setPassphrase(String)` — se rellena con el
passphrase activo, útil para que la propia herramienta de Vídeo de *este*
dispositivo pueda reabrir el stream. **Pero** — verificado con `javap -c`
sobre `gov.tak.api.video.cot.ConnectionEntryDetail`, el método
`toCotDetail(...)` **nunca invoca `getPassphrase()`** entre sus
`invokevirtual` sobre `ConnectionEntry` (sí invoca `getAddress`, `getUID`,
`getAlias`, `getPort`, `getRoverPort`, `getRtspReliable`,
`getIgnoreEmbeddedKLV`, `getPath`, `getProtocol`, `getNetworkTimeout`,
`getBufferTime` — passphrase no está en esa lista). Es decir: **el
passphrase SRT nunca viaja en el detalle `<__video>` que reciben otros
ATAK** — es una limitación real del SDK, no de este plugin. Otro operador
que reciba el CoT vería un stream SRT anunciado pero necesitaría el
passphrase por otro canal (voz, chat) para poder abrirlo. Documentado aquí
en vez de asumido en silencio.

### Decisión 7: los 6 callbacks de `ConnectChecker` llegan al hilo principal — y `isStreaming()` no se autocorrige

Verificado en `RtspClient.kt` y `SrtClient.kt`: **ambos** envuelven cada
llamada a `connectChecker.on...()` en `onMainThread { ... }`, definido en
`common/.../Extensions.kt` como:

```kotlin
suspend fun onMainThread(code: () -> Unit) {
  withContext(Dispatchers.Main) { code() }
}
```

Así que los 6 overrides de `ConnectChecker` en `IcuNgPlugin` pueden tocar
Views directamente, sin `Handler`/`runOnUiThread` propio — confirmado, no
asumido.

Pero verificado también en `Camera2Base.java`: el booleano `streaming` (lo
que devuelve `isStreaming()`) solo lo cambian las llamadas explícitas a
`startStream()`/`stopStream()` de **este plugin** — nunca se auto-corrige
cuando el cliente de red falla o se desconecta por su cuenta
(`onConnectionFailed`/`onDisconnect` no tocan ese booleano, son solo
notificaciones). Hasta ahora los 6 métodos de `ConnectChecker` son stubs
vacíos (desde la Fase 1) — así que hoy, si el servidor cierra la conexión o
falla la autenticación, `camera.isStreaming()` se queda en `true` para
siempre aunque ya no se esté emitiendo nada, lo que además mantiene vivo
incorrectamente el cono `<sensor>` y el registro en `IVideoConnectionManager`
(ambos dependen de `isStreaming()`/`isStreamingWithFix()`). Esta fase cierra
ese hueco real — no es solo "texto de estado bonito", es una corrección de
estado: `onConnectionFailed`, `onDisconnect` y `onAuthError` llaman a
`stopStreamOnly()` (que ya existe desde la Fase 3, es idempotente — revisado:
sus guards `if (connectionEntry != null)`/`if (streamToggleButton != null)`
hacen que una segunda llamada no haga nada raro) para forzar
`camera.stopStream()` y dejar todo el estado (botón, `ConnectionEntry`, cono)
consistente con la realidad.

**Tech Stack:** Mismas dependencias ya declaradas (`RootEncoder:library:2.8.1`,
que ya trae los módulos `rtsp`/`srt` transitivamente — verificado en
`library/build.gradle.kts`: `api(project(":rtsp"))`, `api(project(":srt"))`).
Nada nuevo en `build.gradle`. Del lado de ATAK: mismas clases ya usadas en
la Fase 3 (`ConnectionEntry`, `ConnectionEntryBase.Protocol`, ahora también
`ConnectionEntryBase.Protocol.SRT`), verificadas con `javap` en esta sesión.

## Global Constraints

- Todo lo de `icu_ng_atak` (emulador) y `tools/atak-plugin-dev` (contenedor)
  de las fases anteriores aplica igual — `doctor` primero siempre.
- El gotcha de `SurfaceHolder`/`surfaceCreated()` de las Fases 1/3/4 sigue
  aplicando sin cambios — sigue siendo el único sitio donde se llama a
  `startPreview()`/`replaceView(OpenGlView)`.
- `onStop()` (el plugin entero se detiene) sigue parando todo sin excepción,
  sin cambios respecto a fases anteriores.
- El campo de URL sigue siendo texto libre (no se generan querystrings
  automáticamente) — pero el selector de protocolo reescribe el **esquema**
  (`rtsp://`/`rtsps://`/`srt://`) de lo que haya escrito, preservando el
  resto, cada vez que cambia la selección.
- Verificación en dispositivo (Task 6) necesita un MediaMTX con RTSPS y SRT
  habilitados de verdad, no el `docker run` a pelo de fases anteriores — ver
  Task 6 para el certificado autofirmado y las variables `MTX_*` exactas
  (verificadas contra `mediamtx.org/docs/usage/configuration`: "Parameters
  in maps can be overridden by using underscores, in the following way:
  `MTX_PATHS_TEST_SOURCE=rtsp://myurl`").

## File Structure

```
icu-replacement/
└── app/src/main/
    ├── java/com/carlosgt/atak/icung/plugin/
    │   └── IcuNgPlugin.java        (modificar: todas las Tasks)
    └── res/
        ├── layout/main_layout.xml  (modificar: Task 1)
        └── values/
            ├── strings.xml         (modificar: Task 1)
            └── arrays.xml          (crear: Task 1)
```

`SelfVideoDetailHandler.java` y `SelfSensorFovController.java` (Fase 3): sin
cambios — ver Architecture.

## Task 1: UI — selector de protocolo, campos condicionales, y texto de estado

**Files:**
- Modify: `icu-replacement/app/src/main/res/layout/main_layout.xml`
- Modify: `icu-replacement/app/src/main/res/values/strings.xml`
- Create: `icu-replacement/app/src/main/res/values/arrays.xml`
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Produces: vistas `protocol_spinner`, `trust_all_certs_checkbox`,
  `srt_encryption_row`, `srt_passphrase_input`, `srt_encryption_spinner`,
  `status_text`; campos `protocolSpinner`, `trustAllCertsCheckbox`,
  `srtEncryptionRow`, `srtPassphraseInput`, `srtEncryptionSpinner`,
  `statusText`, `suppressProtocolListener`; métodos `setStatus(String)`,
  `onProtocolSelected(int)`, `rewriteUrlScheme(String, int)` — la Task 2 los
  usa para bloquear el selector y restaurar el estado al reenganchar una
  cámara en segundo plano; la Task 3 usa `setStatus`; la Task 4 usa
  `setStatus` desde los callbacks de `ConnectChecker`.

- [ ] **Step 1: Reescribir `main_layout.xml` con los campos nuevos**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.pedro.library.view.OpenGlView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:background="#80000000"
        android:padding="4dp">

        <TextView
            android:id="@+id/status_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:paddingBottom="4dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <Spinner
                android:id="@+id/protocol_spinner"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />

            <EditText
                android:id="@+id/rtsp_url_input"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:inputType="textUri"
                android:hint="rtsp://host:puerto/stream"
                android:text="rtsp://10.0.2.2:8554/icung"
                android:textColor="#FFFFFF"
                android:textColorHint="#AAAAAA" />

            <Button
                android:id="@+id/stream_toggle_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/start_stream" />

        </LinearLayout>

        <CheckBox
            android:id="@+id/trust_all_certs_checkbox"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/trust_all_certs"
            android:textColor="#FFFFFF"
            android:visibility="gone" />

        <LinearLayout
            android:id="@+id/srt_encryption_row"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:visibility="gone">

            <EditText
                android:id="@+id/srt_passphrase_input"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:inputType="textPassword"
                android:hint="@string/srt_passphrase_hint"
                android:textColor="#FFFFFF"
                android:textColorHint="#AAAAAA" />

            <Spinner
                android:id="@+id/srt_encryption_spinner"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />

        </LinearLayout>

    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 2: Crear `arrays.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="protocol_entries">
        <item>RTSP</item>
        <item>RTSPS (TLS)</item>
        <item>SRT</item>
    </string-array>

    <string-array name="srt_encryption_entries">
        <item>AES-128</item>
        <item>AES-192</item>
        <item>AES-256</item>
    </string-array>
</resources>
```

- [ ] **Step 3: Añadir strings nuevos**

En `strings.xml`, justo antes de `</resources>`:

```xml
    <string name="trust_all_certs">Confiar en cualquier certificado (solo pruebas/LAN)</string>
    <string name="srt_passphrase_hint">Passphrase SRT (10-79 caracteres, vacío = sin cifrar)</string>
    <string name="status_idle">Detenido</string>
    <string name="status_connecting">Conectando…</string>
    <string name="status_streaming">Emitiendo</string>
    <string name="status_disconnected">Desconectado</string>
    <string name="status_error_format">Error: %1$s</string>
```

- [ ] **Step 4: Imports nuevos en `IcuNgPlugin.java`**

Añadir junto a los imports de `android.widget.*` ya existentes:

```java
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
```

- [ ] **Step 5: Campos nuevos**

Junto a `Button streamToggleButton;`:

```java
    Button streamToggleButton;
    Spinner protocolSpinner;
    CheckBox trustAllCertsCheckbox;
    View srtEncryptionRow;
    EditText srtPassphraseInput;
    Spinner srtEncryptionSpinner;
    TextView statusText;
    boolean suppressProtocolListener;
```

- [ ] **Step 6: Añadir `setStatus`, `onProtocolSelected`, `rewriteUrlScheme`**

Métodos nuevos, junto a `stopStreamOnly()`:

```java
    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }

    private void onProtocolSelected(int position) {
        trustAllCertsCheckbox.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        srtEncryptionRow.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        rtspUrlInput.setText(rewriteUrlScheme(rtspUrlInput.getText().toString(), position));
    }

    // position: 0=RTSP, 1=RTSPS, 2=SRT (mismo orden que R.array.protocol_entries)
    private static String rewriteUrlScheme(String url, int position) {
        String scheme = position == 1 ? "rtsps" : position == 2 ? "srt" : "rtsp";
        int schemeEnd = url.indexOf("://");
        String rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
        return scheme + "://" + rest;
    }
```

- [ ] **Step 7: Conectar las vistas nuevas en `showPane()`**

Dentro de `showPane()`, justo después de
`streamToggleButton.setOnClickListener(v -> toggleStream());` y **antes**
de `final boolean resumingBackgroundCamera = camera != null;`:

```java
            protocolSpinner = paneView.findViewById(R.id.protocol_spinner);
            trustAllCertsCheckbox = paneView.findViewById(R.id.trust_all_certs_checkbox);
            srtEncryptionRow = paneView.findViewById(R.id.srt_encryption_row);
            srtPassphraseInput = paneView.findViewById(R.id.srt_passphrase_input);
            srtEncryptionSpinner = paneView.findViewById(R.id.srt_encryption_spinner);
            statusText = paneView.findViewById(R.id.status_text);

            ArrayAdapter<CharSequence> protocolAdapter = ArrayAdapter.createFromResource(
                    pluginContext, R.array.protocol_entries, android.R.layout.simple_spinner_item);
            protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            protocolSpinner.setAdapter(protocolAdapter);

            ArrayAdapter<CharSequence> encryptionAdapter = ArrayAdapter.createFromResource(
                    pluginContext, R.array.srt_encryption_entries, android.R.layout.simple_spinner_item);
            encryptionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            srtEncryptionSpinner.setAdapter(encryptionAdapter);

            protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (suppressProtocolListener) return;
                    onProtocolSelected(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            });

            setStatus(pluginContext.getString(R.string.status_idle));
```

Y, dentro de la rama `if (resumingBackgroundCamera) { ... }` (que en esta
Task todavía no distingue protocolo — eso llega en la Task 2), añadir una
línea al final:

```java
            if (resumingBackgroundCamera) {
                rtspUrlInput.setText(activeStreamUrl);
                streamToggleButton.setText(R.string.stop_stream);
                setStatus(pluginContext.getString(R.string.status_streaming));
            } else {
```

- [ ] **Step 8: Compilar**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/res/layout/main_layout.xml \
        icu-replacement/app/src/main/res/values/strings.xml \
        icu-replacement/app/src/main/res/values/arrays.xml \
        icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): selector de protocolo/cifrado y texto de estado en el panel

Anade el Spinner de protocolo (RTSP/RTSPS/SRT), los campos
condicionales de RTSPS (confiar en certificado) y SRT (passphrase +
tipo de cifrado), y un TextView de estado - todo por ahora solo
visual/de wiring: cambiar de protocolo reescribe el esquema de la URL
y muestra/oculta los campos que tocan, pero la construccion de la
camara y el arranque del stream siguen sin distinguir protocolo (Task
2 en adelante).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 2: `Camera2Base` genérico + construcción condicional + bloqueo del selector en el límite SRT

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `protocolSpinner`, `trustAllCertsCheckbox`, `srtEncryptionRow`,
  `srtPassphraseInput`, `srtEncryptionSpinner`, `statusText`,
  `suppressProtocolListener`, `setStatus(String)`, `onProtocolSelected(int)`
  (Task 1).
- Produces: campo `camera` ahora tipado `Camera2Base`; campos
  `activeProtocol` (int), `activeTrustAllCerts` (boolean),
  `activeSrtPassphrase` (String), `activeSrtEncryptionType`
  (`EncryptionType`), `lastValidProtocolPosition` (int) — la Task 3 los lee
  para saber qué cliente concreto y qué configuración aplicar antes de
  emitir; la Task 5 lee `activeProtocol` para decidir si anunciar CoT.

- [ ] **Step 1: Imports nuevos**

```java
import com.pedro.library.base.Camera2Base;
import com.pedro.library.srt.SrtCamera2;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.srt.srt.packets.control.handshake.EncryptionType;
```

- [ ] **Step 2: Cambiar el tipo de `camera` y añadir los campos "active*"**

```java
    RtspCamera2 camera;
```

pasa a:

```java
    Camera2Base camera;
```

Y, junto a `String activeStreamUrl;`:

```java
    String activeStreamUrl;
    int activeProtocol; // 0=RTSP, 1=RTSPS, 2=SRT - mismo orden que R.array.protocol_entries
    boolean activeTrustAllCerts;
    String activeSrtPassphrase = "";
    EncryptionType activeSrtEncryptionType = EncryptionType.AES128;
    int lastValidProtocolPosition;

    private static final EncryptionType[] SRT_ENCRYPTION_TYPES = {
            EncryptionType.AES128, EncryptionType.AES192, EncryptionType.AES256
    };

    private static int indexOfEncryptionType(EncryptionType type) {
        for (int i = 0; i < SRT_ENCRYPTION_TYPES.length; i++) {
            if (SRT_ENCRYPTION_TYPES[i] == type) return i;
        }
        return 0;
    }
```

- [ ] **Step 3: Bloquear el límite SRT en `onProtocolSelected`**

Reemplazar el método completo (de la Task 1) por:

```java
    private void onProtocolSelected(int position) {
        boolean crossingSrtBoundary = camera != null
                && (position == 2) != (camera instanceof SrtCamera2);
        if (crossingSrtBoundary) {
            revertProtocolSelection();
            setStatus(pluginContext.getString(R.string.status_error_format,
                    "cierra el panel para cambiar a/desde SRT mientras hay una camara activa"));
            return;
        }
        lastValidProtocolPosition = position;
        trustAllCertsCheckbox.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        srtEncryptionRow.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        rtspUrlInput.setText(rewriteUrlScheme(rtspUrlInput.getText().toString(), position));
    }

    private void revertProtocolSelection() {
        suppressProtocolListener = true;
        protocolSpinner.setSelection(lastValidProtocolPosition);
        suppressProtocolListener = false;
    }
```

- [ ] **Step 4: Reescribir `showPane()` completo**

Reemplazar el método completo:

```java
    private void showPane() {
        // instantiate the plugin view if necessary
        if(templatePane == null) {
            // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
            // In this case, using it is not necessary - but I am putting it here to remind
            // developers to look at this Inflator

            View paneView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.main_layout, null);

            OpenGlView previewView = paneView.findViewById(R.id.preview_view);

            rtspUrlInput = paneView.findViewById(R.id.rtsp_url_input);
            streamToggleButton = paneView.findViewById(R.id.stream_toggle_button);
            streamToggleButton.setOnClickListener(v -> toggleStream());

            protocolSpinner = paneView.findViewById(R.id.protocol_spinner);
            trustAllCertsCheckbox = paneView.findViewById(R.id.trust_all_certs_checkbox);
            srtEncryptionRow = paneView.findViewById(R.id.srt_encryption_row);
            srtPassphraseInput = paneView.findViewById(R.id.srt_passphrase_input);
            srtEncryptionSpinner = paneView.findViewById(R.id.srt_encryption_spinner);
            statusText = paneView.findViewById(R.id.status_text);

            ArrayAdapter<CharSequence> protocolAdapter = ArrayAdapter.createFromResource(
                    pluginContext, R.array.protocol_entries, android.R.layout.simple_spinner_item);
            protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            protocolSpinner.setAdapter(protocolAdapter);

            ArrayAdapter<CharSequence> encryptionAdapter = ArrayAdapter.createFromResource(
                    pluginContext, R.array.srt_encryption_entries, android.R.layout.simple_spinner_item);
            encryptionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            srtEncryptionSpinner.setAdapter(encryptionAdapter);

            protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (suppressProtocolListener) return;
                    onProtocolSelected(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            });

            // Fase 4: if camera already exists, it's one tearDownPane() left
            // alive in the background - reattach it instead of building a
            // new one, and restore the UI (URL, boton, protocolo, cifrado)
            // to reflect that it's still streaming.
            final boolean resumingBackgroundCamera = camera != null;
            if (resumingBackgroundCamera) {
                rtspUrlInput.setText(activeStreamUrl);
                streamToggleButton.setText(R.string.stop_stream);
                setStatus(pluginContext.getString(R.string.status_streaming));

                suppressProtocolListener = true;
                protocolSpinner.setSelection(activeProtocol);
                suppressProtocolListener = false;
                lastValidProtocolPosition = activeProtocol;
                trustAllCertsCheckbox.setVisibility(activeProtocol == 1 ? View.VISIBLE : View.GONE);
                trustAllCertsCheckbox.setChecked(activeTrustAllCerts);
                srtEncryptionRow.setVisibility(activeProtocol == 2 ? View.VISIBLE : View.GONE);
                srtPassphraseInput.setText(activeSrtPassphrase);
                srtEncryptionSpinner.setSelection(indexOfEncryptionType(activeSrtEncryptionType));
            } else {
                lastValidProtocolPosition = protocolSpinner.getSelectedItemPosition();
                setStatus(pluginContext.getString(R.string.status_idle));
                camera = protocolSpinner.getSelectedItemPosition() == 2
                        ? new SrtCamera2(previewView, this)
                        : new RtspCamera2(previewView, this);
                camera.prepareVideo();
                camera.prepareAudio();
            }

            // RootEncoder needs the SurfaceView's real Surface to exist before
            // startPreview()/replaceView() can touch it - calling it right
            // after inflate() crashes with NullPointerException:
            // getSurfaceTexture(...) must not be null, because Android
            // creates the Surface asynchronously on a later layout pass, not
            // synchronously when the view is inflated. Wait for
            // surfaceCreated(), same pattern RootEncoder's own demo app uses
            // for TextureView's onSurfaceTextureAvailable. This is the only
            // place startPreview()/replaceView() are ever called - see
            // tearDownPane() for why nothing tries to resume across a full
            // rebuild of paneView/previewView themselves, only camera.
            previewView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    if (resumingBackgroundCamera) {
                        camera.replaceView(previewView);
                    } else if (!camera.isOnPreview()) {
                        camera.startPreview(CameraHelper.Facing.BACK);
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    // no-op
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    // no-op
                }
            });

            templatePane = new PaneBuilder(paneView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, new IHostUIService.IPaneLifecycleListener() {
                @Override
                public void onPaneVisible(boolean visible) {
                    if (!visible) {
                        tearDownPane();
                    }
                }

                @Override
                public void onPaneClose() {
                    tearDownPane();
                }
            });
        }
    }
```

- [ ] **Step 5: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Si falla con algo sobre `getStreamClient()` o
tipos incompatibles en esta Task, revisar que `camera` quedó declarado como
`Camera2Base` en el campo de clase (Step 2) y no solo en variables locales.

- [ ] **Step 6: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): construccion de camara RTSP/SRT segun protocolo elegido

camera pasa de RtspCamera2 a Camera2Base - verificado que toda la API
que usa el ciclo de vida de las Fases 3/4 (prepareVideo/prepareAudio/
startPreview/stopPreview/startStream/stopStream/isStreaming/
isOnPreview/replaceView) esta declarada en Camera2Base, no en las
subclases, asi que ese ciclo de vida no cambia. showPane() construye
RtspCamera2 (RTSP y RTSPS son la misma clase, solo cambia el esquema
de la URL) o SrtCamera2 segun la seleccion del spinner en el momento
de construir - cambiar hacia/desde SRT con una camara ya viva se
rechaza (revierte el spinner) porque requeriria reconstruir la
instancia en caliente, fuera de alcance de esta fase.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 3: Aplicar cifrado real antes de emitir + validar protocolo↔URL

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `camera` (`Camera2Base`, Task 2), `activeProtocol`/
  `activeTrustAllCerts`/`activeSrtPassphrase`/`activeSrtEncryptionType`
  (Task 2), `trustAllCertsCheckbox`/`srtPassphraseInput`/
  `srtEncryptionSpinner`/`SRT_ENCRYPTION_TYPES` (Tasks 1-2),
  `setStatus(String)` (Task 1).
- Produces: método `parseUrlScheme(String)` — la Task 5 no lo necesita, pero
  queda disponible si una fase futura reutiliza el parseo.

- [ ] **Step 1: Import nuevos para TLS**

```java
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
```

- [ ] **Step 2: `TrustManager` permisivo constante**

Junto a `SRT_ENCRYPTION_TYPES`:

```java
    private static final TrustManager TRUST_ALL_CERTS = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
```

- [ ] **Step 3: Helper de esquema de URL**

Junto a `rewriteUrlScheme`:

```java
    private static String parseUrlScheme(String url) {
        int schemeEnd = url.indexOf("://");
        return schemeEnd >= 0 ? url.substring(0, schemeEnd) : "";
    }
```

- [ ] **Step 4: Reescribir `toggleStream()`**

Reemplazar el método completo:

```java
    private void toggleStream() {
        if (camera.isStreaming()) {
            stopStreamOnly();
        } else {
            String url = rtspUrlInput.getText().toString();
            int protocol = protocolSpinner.getSelectedItemPosition();

            String expectedScheme = protocol == 1 ? "rtsps" : protocol == 2 ? "srt" : "rtsp";
            if (!expectedScheme.equals(parseUrlScheme(url))) {
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "la URL debe empezar por " + expectedScheme + "://"));
                return;
            }

            String passphrase = srtPassphraseInput.getText().toString();
            if (protocol == 2 && !passphrase.isEmpty()
                    && (passphrase.length() < 10 || passphrase.length() > 79)) {
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "el passphrase SRT debe tener entre 10 y 79 caracteres"));
                return;
            }

            EncryptionType encryptionType = SRT_ENCRYPTION_TYPES[srtEncryptionSpinner.getSelectedItemPosition()];
            if (protocol == 1) {
                ((RtspStreamClient) camera.getStreamClient())
                        .addCertificates(trustAllCertsCheckbox.isChecked() ? TRUST_ALL_CERTS : null);
            } else if (protocol == 2 && !passphrase.isEmpty()) {
                ((SrtStreamClient) camera.getStreamClient()).setPassphrase(passphrase, encryptionType);
            }

            activeStreamUrl = url;
            activeProtocol = protocol;
            activeTrustAllCerts = trustAllCertsCheckbox.isChecked();
            activeSrtPassphrase = passphrase;
            activeSrtEncryptionType = encryptionType;

            camera.startStream(url);
            streamToggleButton.setText(R.string.stop_stream);

            connectionEntry = protocol == 1 ? null : buildConnectionEntry(url, protocol, passphrase);
            if (connectionEntry != null) {
                videoConnectionManager.addConnectionEntry(connectionEntry);
            }
            sensorFovController.refresh(true);
        }
    }
```

- [ ] **Step 5: Reescribir `buildConnectionEntry`**

Reemplazar el método completo (la firma cambia — la Task 5 detalla el
porqué del protocolo/passphrase, pero el cambio de firma vive aquí porque
`toggleStream()` ya lo necesita):

```java
    private ConnectionEntry buildConnectionEntry(String url, int protocol, String passphrase) {
        android.net.Uri parsed = android.net.Uri.parse(url);
        ConnectionEntry.Protocol cotProtocol = protocol == 2
                ? ConnectionEntry.Protocol.SRT
                : ConnectionEntry.Protocol.RTSP;

        String path = parsed.getQueryParameter("streamid");
        if (path == null || path.isEmpty()) {
            path = parsed.getPath();
        }

        ConnectionEntry.Builder builder = new ConnectionEntry.Builder()
                .setUID(pluginContext.getPackageName() + "." + System.currentTimeMillis())
                .setAlias("ICU NG")
                .setAddress(parsed.getHost())
                .setPort(parsed.getPort() > 0 ? parsed.getPort() : cotProtocol.getDefaultPort())
                .setPath(path)
                .setProtocol(cotProtocol)
                .setSource(ConnectionEntry.Source.EXTERNAL);
        if (protocol == 2 && !passphrase.isEmpty()) {
            builder.setPassphrase(passphrase);
        }
        return builder.build();
    }
```

- [ ] **Step 6: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): aplicar certificado RTSPS / passphrase SRT antes de emitir

toggleStream() valida que el esquema de la URL coincide con el
protocolo elegido, y para SRT que el passphrase (si no esta vacio)
mide entre 10 y 79 caracteres - verificado contra SrtClient.setPassphrase
que lanza IllegalArgumentException fuera de ese rango (el comentario
de la propia libreria sobre "vacio para desactivar" resulta enganoso
para este metodo concreto: con longitud 0 tambien lanza, la forma real
de desactivar el cifrado es no llamar a setPassphrase en absoluto).
Para RTSPS, aplica siempre RtspStreamClient.addCertificates(...) - con
el TrustManager permisivo si el checkbox esta marcado, o null (validacion
estandar contra CAs del sistema) si no, para que desmarcar el checkbox
tras haberlo usado revierta correctamente en el siguiente arranque
sobre la misma camara.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 4: Feedback de estado real vía `ConnectChecker`

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `setStatus(String)` (Task 1), `stopStreamOnly()` (Fase 3, sin
  cambios).

- [ ] **Step 1: Reescribir los 6 métodos de `ConnectChecker`**

Reemplazar los 6 métodos completos (actualmente todos son stubs vacíos con
el comentario `// Sin uso hasta la Fase 2 (streaming RTSP)`):

```java
    @Override
    public void onConnectionStarted(String url) {
        setStatus(pluginContext.getString(R.string.status_connecting));
    }

    @Override
    public void onConnectionSuccess() {
        setStatus(pluginContext.getString(R.string.status_streaming));
    }

    @Override
    public void onConnectionFailed(String reason) {
        setStatus(pluginContext.getString(R.string.status_error_format, reason));
        stopStreamOnly();
    }

    @Override
    public void onDisconnect() {
        setStatus(pluginContext.getString(R.string.status_disconnected));
        stopStreamOnly();
    }

    @Override
    public void onAuthError() {
        setStatus(pluginContext.getString(R.string.status_error_format, "autenticacion fallida"));
        stopStreamOnly();
    }

    @Override
    public void onAuthSuccess() {
        setStatus(pluginContext.getString(R.string.status_streaming));
    }
```

- [ ] **Step 2: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
fix(icu-replacement): conectar ConnectChecker - feedback real y correccion de isStreaming()

Los 6 callbacks de ConnectChecker eran stubs vacios desde la Fase 1
pese al comentario "Sin uso hasta la Fase 2". Ahora, ademas de
actualizar el texto de estado del panel, onConnectionFailed/
onDisconnect/onAuthError llaman a stopStreamOnly() - verificado en
Camera2Base que el booleano streaming solo lo cambian las llamadas
explicitas a startStream()/stopStream() de este plugin, nunca se
auto-corrige cuando el cliente de red falla o se desconecta por su
cuenta. Sin esto, isStreaming() se quedaba en true para siempre tras
un fallo real, manteniendo vivo incorrectamente el cono <sensor> y el
registro en IVideoConnectionManager. Verificado tambien que los 6
callbacks llegan ya en el hilo principal (onMainThread{} en RtspClient
y SrtClient), asi que tocar Views aqui es seguro sin Handler propio.
stopStreamOnly() ya era idempotente desde la Fase 3 (sus guards
existentes toleran llamadas repetidas), asi que no hace falta
deduplicar nada.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 5: Anuncio CoT/`IVideoConnectionManager` — RTSP y SRT, RTSPS documentadamente no

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `buildConnectionEntry(String, int, String)` (Task 3, ya construida
  correctamente — esta Task documenta y verifica el comportamiento, no
  cambia código de producción salvo un comentario).

Esta Task **no añade código nuevo** — el comportamiento correcto
(`connectionEntry == null` para RTSPS, `Protocol.SRT` + passphrase local
para SRT) ya quedó implementado en la Task 3 porque `toggleStream()` y
`buildConnectionEntry()` lo necesitaban desde el principio para compilar
con la firma correcta. Esta Task es documentación en código (un comentario
que dispersa la Decisión 6 justo donde importa) más la verificación en
dispositivo de que el comportamiento es el esperado — se hace en la Task 6
junto con el resto de la verificación end-to-end para no repetir el ciclo
deploy/sync/Load tres veces.

- [ ] **Step 1: Comentario en `toggleStream()` explicando el `null` de RTSPS**

En `toggleStream()`, justo encima de la línea
`connectionEntry = protocol == 1 ? null : buildConnectionEntry(url, protocol, passphrase);`:

```java
            // RTSPS (protocol == 1) nunca se anuncia por CoT/IVideoConnectionManager:
            // verificado con javap que ConnectionEntryBase.Protocol no tiene una
            // constante RTSPS (solo RTSP/RTMP/RTMPS/SRT/...), y que Protocol.RTSP.toURL()
            // siempre construye literalmente "rtsp://" - anunciar RTSPS como
            // Protocol.RTSP le daria a otros ATAK una URL rota que su cliente de
            // Video no podria abrir contra un servidor que exige TLS. El push RTSPS
            // en si sigue funcionando igual - solo se deja de anunciar en el mapa.
            connectionEntry = protocol == 1 ? null : buildConnectionEntry(url, protocol, passphrase);
```

- [ ] **Step 2: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
docs(icu-replacement): documentar por que RTSPS no se anuncia por CoT

Sin cambio de comportamiento - buildConnectionEntry()/toggleStream()
(Task 3) ya dejaban connectionEntry en null para RTSPS porque
ConnectionEntryBase.Protocol (verificado con javap contra el SDK real)
no tiene una constante RTSPS. Este commit solo deja la evidencia en el
propio punto de la decision para quien lea el codigo despues.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 6: Deploy y verificación en dispositivo — RTSP, RTSPS (cert autofirmado) y SRT (passphrase) reales

**Files:** ninguno (verifica en el dispositivo, y contra un MediaMTX real
con RTSPS/SRT habilitados, lo que dejaron las Tareas 1-5).

**Interfaces:**
- Consumes: APK de `icu-replacement` con selector de protocolo, cifrado real
  y feedback de estado (Tareas 1-4), y anuncio CoT correcto por protocolo
  (Tarea 5).

- [ ] **Step 1: Confirmar que el entorno sigue sano**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev doctor
```

- [ ] **Step 2: Generar un certificado autofirmado para RTSPS**

Comando documentado por el propio `mediamtx.yml` de referencia:

```bash
cd "D:\Documents\atak-plugins\tools\atak-plugin-dev"
openssl genrsa -out server.key 2048
openssl req -new -x509 -sha256 -key server.key -out server.crt -days 3650 -subj "/CN=10.0.2.2"
```

- [ ] **Step 3: Levantar un MediaMTX con RTSP + RTSPS + SRT (passphrase)**

Variables `MTX_*` verificadas contra `mediamtx.org/docs/usage/configuration`
("Parameters in maps can be overridden by using underscores, in the
following way: `MTX_PATHS_TEST_SOURCE=rtsp://myurl`"):

```powershell
docker run --rm -d --name icung-test-mediamtx `
  -p 8554:8554 -p 8322:8322 -p 8890:8890/udp `
  -e MTX_RTSPENCRYPTION=optional `
  -e MTX_RTSPSERVERCERT=/server.crt `
  -e MTX_RTSPSERVERKEY=/server.key `
  -e MTX_PATHS_ICUNG_SRTPUBLISHPASSPHRASE=icungtestpass123 `
  -v "D:\Documents\atak-plugins\tools\atak-plugin-dev\server.crt:/server.crt" `
  -v "D:\Documents\atak-plugins\tools\atak-plugin-dev\server.key:/server.key" `
  bluenviron/mediamtx
```

- [ ] **Step 4: Build + install + stage, sync + Load**

```powershell
docker compose exec atak-dev deploy icu-replacement
```

Sync + Load en ATAK igual que en las fases anteriores.

- [ ] **Step 5: RTSP — confirmar que no hay regresión**

Abrir el panel, dejar el selector en "RTSP" (por defecto), pulsar "Emitir"
con `rtsp://10.0.2.2:8554/icung`.

Expected: texto de estado pasa por "Conectando…" y luego "Emitiendo";
`docker logs icung-test-mediamtx --tail 10` muestra `is publishing to path
'icung'`, 2 tracks. Pulsar "Detener": estado vuelve a "Detenido" (o similar),
MediaMTX registra `destroyed`/`closed`. Comportamiento idéntico a las Fases
2-4 — sin regresión.

- [ ] **Step 6: RTSPS — certificado no confiado falla limpio, "confiar" funciona**

Cambiar el selector a "RTSPS (TLS)" (la URL se reescribe sola a
`rtsps://10.0.2.2:8554/icung` — **cambiar el puerto a mano a 8322**, el de
`rtspsAddress`: `rtsps://10.0.2.2:8322/icung`). Dejar el checkbox "Confiar en
cualquier certificado" **sin marcar** y pulsar "Emitir".

Expected: el estado pasa a "Error: ..." (el `SSLHandshakeException` de
validación de cadena, propagado desde `onConnectionFailed`) — confirma que,
por defecto, un certificado autofirmado no confiado **falla** en vez de
conectar en silencio. El botón vuelve a "Emitir" solo (confirma la Task 4:
`onConnectionFailed` llama a `stopStreamOnly()`).

Marcar el checkbox y pulsar "Emitir" otra vez con la misma URL.

Expected: "Conectando…" → "Emitiendo". `docker logs icung-test-mediamtx`
muestra la sesión publicando en el puerto RTSPS. Pulsar "Detener": corte
limpio.

Comprobar también que, con RTSPS emitiendo, **no** aparece ningún
`ConnectionEntry` para él: abrir la herramienta de Vídeo nativa de ATAK y
confirmar que ICU NG no aparece en la lista mientras se emite por RTSPS
(Decisión 6) — sí debe verse la primera vez que se emitió por RTSP (Step 5,
si no se cerró el panel del todo entre pasos puede que ese registro previo
siga listado; lo relevante es que RTSPS no añade uno nuevo).

- [ ] **Step 7: SRT — passphrase corto rechazado en cliente, passphrase válido funciona**

Cerrar el panel del todo (para liberar la cámara RTSPS y poder cruzar al
límite SRT — Decisión 3) y reabrirlo. Cambiar el selector a "SRT" (URL se
reescribe a `srt://10.0.2.2:8554/icung` — **cambiar el puerto a mano a
8890**: `srt://10.0.2.2:8890?streamid=icung`, formato de streamid que
`SrtClient` prioriza sobre el path, verificado contra su código). Escribir
un passphrase corto (p.ej. `corto`) y pulsar "Emitir".

Expected: estado "Error: el passphrase SRT debe tener entre 10 y 79
caracteres" — confirma la validación de cliente de la Task 3, sin llegar a
invocar `setPassphrase()` (que lanzaría una excepción no capturada si se
llamara con esa longitud).

Corregir el passphrase a `icungtestpass123` (el mismo que
`srtPublishPassphrase` del Step 3) y pulsar "Emitir".

Expected: "Conectando…" → "Emitiendo". `docker logs icung-test-mediamtx`
muestra la sesión SRT publicando en el path `icung`. Abrir la herramienta de
Vídeo nativa y confirmar que **sí** aparece un `ConnectionEntry` para este
stream (a diferencia de RTSPS) — protocolo SRT, Decisión 6. Pulsar
"Detener": corte limpio, estado y botón vuelven a reposo.

Repetir con un passphrase equivocado (p.ej. `passphraseincorrecto1`, también
de longitud válida 10-79 pero distinto al configurado en MediaMTX) para
confirmar que el servidor rechaza la conexión y el estado muestra el error
correspondiente (no un "Emitiendo" falso).

- [ ] **Step 8: Verificación por log — sin crash**

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep -i icung' 2>&1 | Select-String -Pattern "FATAL|SecurityException"
```

Expected: sin resultados, en ninguno de los tres protocolos probados.

- [ ] **Step 9: Limpiar**

```powershell
docker stop icung-test-mediamtx
Remove-Item "D:\Documents\atak-plugins\tools\atak-plugin-dev\server.key", "D:\Documents\atak-plugins\tools\atak-plugin-dev\server.crt"
```

Sin commit en esta tarea — solo verificación de lo que dejaron las Tareas
1-5.

---

## Self-Review

- **Cobertura del alcance pedido:** "RTSPS/SRT sobre RootEncoder" → Tasks 2-3
  (construcción condicional + cifrado real aplicado y validado); "UI para
  elegir protocolo/cifrado" → Task 1 (más el bloqueo del límite SRT en la
  Task 2); "mejor feedback visual del estado (conectando/emitiendo/error)" →
  Task 4. Investigación contra RootEncoder real antes de escribir el plan →
  Decisiones 1-5 y 7, todas con código fuente citado verbatim de la etiqueta
  `2.8.1`. Encaje con el ciclo de vida de las Fases 3/4 sin romperlo →
  Decisión 1 (por qué `Camera2Base` genérico no cambia nada de
  `tearDownPane()`/`stopCameraAndStream()`/segundo plano) y Decisión 3 (el
  único límite nuevo, acotado explícitamente).
- **Placeholders:** ninguno — cada Task tiene el código Java/XML completo, no
  descripciones. `TRUST_ALL_CERTS`, `SRT_ENCRYPTION_TYPES`,
  `rewriteUrlScheme`, `onProtocolSelected`, `buildConnectionEntry`, los 6
  overrides de `ConnectChecker`: todos con cuerpo completo.
- **Consistencia de nombres/tipos:** `activeProtocol`/`protocolSpinner`
  usan enteros `0/1/2` consistentes con `R.array.protocol_entries` en todas
  las Tasks donde aparecen (1, 2, 3, 5) — se documentó explícitamente el
  mapeo en cada sitio en vez de asumir que se recuerda. `camera` se declara
  `Camera2Base` en la Task 2 y se usa así en las Tasks 3-5 (nunca vuelve a
  `RtspCamera2`). `SRT_ENCRYPTION_TYPES`/`indexOfEncryptionType` se declaran
  en la Task 2 y se consumen en las Tasks 3 y 6.
- **Riesgo identificado y no asumido a ciegas:**
  - El comentario oficial de `SrtClient.setPassphrase` ("empty value to
    disable it") es literalmente incorrecto para el método que este plugin
    llama — verificado leyendo el cuerpo real, no confiado al Javadoc
    (Decisión 5). Sin este chequeo, un passphrase vacío habría lanzado
    `IllegalArgumentException` sin capturar la primera vez que alguien
    probara "SRT sin cifrar".
  - `ConnectionEntryBase.Protocol` no tiene RTSPS — verificado con `javap`
    en vez de asumir que "protocolos seguros" se mapean 1:1 (Decisión 6).
  - El passphrase SRT no viaja en el CoT `<__video>` — verificado con
    `javap -c` sobre el propio `toCotDetail()`, no asumido por simetría con
    que `ConnectionEntry` sí tiene el campo (Decisión 6).
  - `isStreaming()` no se autocorrige en fallo/desconexión — verificado
    leyendo `Camera2Base.java` completo, no asumido por "seguro que la
    librería ya maneja esto" (Decisión 7); este era un bug real preexistente
    desde la Fase 1 (los stubs vacíos de `ConnectChecker`), no algo
    introducido por esta fase — se corrige aquí de paso.
  - Los callbacks de `ConnectChecker` sí llegan al hilo principal —
    verificado en vez de añadir `Handler`/`post()` "por si acaso" o, peor,
    asumir que son seguros sin comprobar y arriesgarse a un crash de hilo.
- **Fuera de alcance, deliberadamente:**
  - Cambiar de protocolo en caliente con una cámara SRT/no-SRT ya viva
    (Decisión 3) — requeriría reconstruir `camera` fuera del único punto de
    construcción que las Fases 3/4 dejaron probado.
  - `setTlsHostVerification` como control de UI aparte (Decisión 4) — se
    deja en el valor por defecto de la librería.
  - Subir/seleccionar un certificado de cliente o CA concreta para RTSPS
    (solo se ofrece el extremo "confiar en todo" o "validación estándar del
    sistema") — cubre el caso real (LAN/autofirmado) sin construir un
    selector de certificados completo.
  - Comunicar el passphrase SRT a otros ATAK por algún canal propio del
    plugin (fuera del CoT estándar) — el SDK no lo soporta en `<__video>` y
    construir un canal paralelo es un proyecto aparte.
