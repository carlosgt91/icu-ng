# ICU NG — Fase 2: servidor/push RTSP — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El botón "Emitir" del panel de ICU NG arranca un push RTSP real
(vídeo+audio) desde la cámara ya en preview (Fase 1) hacia un servidor
externo configurable, y "Detener" lo para sin tocar el preview. Verificado
contra un receptor RTSP real (MediaMTX), no solo "compila". Ver
[2026-09-17-icu-ng-design.md](../specs/2026-09-17-icu-ng-design.md) (fase
"Servidor/push RTSP" del diseño general).

**Architecture:** Se reutiliza el mismo objeto `RtspCamera2` que la Fase 1
ya construye y usa para el preview — esta fase solo añade las llamadas de
`prepareVideo()`/`prepareAudio()`/`startStream(url)`/`stopStream()` sobre esa
misma instancia. Nada de esto reemplaza o duplica el manejo de
`startPreview()`/`stopPreview()` de la Fase 1.

### Decisión 1: ¿servidor on-device, push a servidor externo, o ambos?

**Solo push a servidor externo — y no es una elección de diseño, es lo único
que soporta la librería.** Verificado contra el código fuente de
`Camera2Base.java` (rama `master` de
[pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder)):

```java
public void startStream(String url) {
    streaming = true;
    if (!recordController.isRunning()) {
        startEncoders();
    } else {
        requestKeyFrame();
    }
    startStreamImp(url);
    onPreview = true;
}
```

`startStreamImp(url)` es abstracto y lo implementa `RtspCamera2` delegando en
un cliente RTSP (rol *publisher*, conecta hacia fuera) — la propia
documentación del método describe `url` como
`protocol://ip:port/application/streamName`, formato de **destino**, no de
*bind address* de un servidor. El repo no tiene ningún módulo de servidor
(`library`, `rtmp`, `rtsp`, `srt`, `udp`, `whip`, `encoder`, `common` — ninguno
implementa escuchar conexiones entrantes). Esto **confirma** la decisión ya
tomada en la spec de diseño general: el plugin publica, un servidor externo
(tipo MediaMTX / `tak-video-restreamer`) recibe — eso queda fuera de este
subproyecto.

### Decisión 2: ciclo de vida del stream vs. el preview de la Fase 1

**Se reutiliza el mismo `onStop()` de la Fase 1, ampliado — no hay un ciclo
de vida paralelo.** Verificado el motivo exacto por el que esto es seguro,
también contra el código fuente de `stopStream()`:

```java
public void stopStream() {
    if (streaming) {
        streaming = false;
        stopStreamImp();
    }
    if (!recordController.isRunning()) {
        onPreview = !isBackground;
        if (audioInitialized) microphoneManager.stop();
        glInterface.removeMediaCodecSurface();
        glInterface.removeMediaCodecRecordSurface();
        if (glInterface instanceof GlStreamInterface) {
            glInterface.stop();
            cameraManager.closeCamera();
        }
        videoEncoder.stop();
        ...
    }
}
```

El único bloque que cierra la cámara (`cameraManager.closeCamera()`) está
condicionado a `glInterface instanceof GlStreamInterface` — y nuestro
`glInterface` es `OpenGlView`, que implementa `GlInterface` pero **no**
`GlStreamInterface` (son dos clases distintas del paquete
`com.pedro.library.view`; `GlStreamInterface` es para streaming sin vista
visible). Para nosotros, `stopStream()` para el encoder de vídeo/audio y la
conexión de red, pero dejan el preview de la `OpenGlView` intacto. Por eso
basta con llamar a `camera.stopStream()` **antes** del `stopPreview()` que ya
existe en `onStop()` — sin bandera nueva, sin lifecycle separado.

### Decisión 3: la simplificación pendiente de la Fase 1

**Se deja registrada explícitamente, no se corrige en esta fase — pero su
gravedad cambia.** La Fase 1 documentó como simplificación conocida que la
cámara solo se libera en `onStop()` del plugin entero, no al ocultar
simplemente el panel. Hasta ahora eso significaba "la cámara se queda
encendida de fondo" (molesto, gasto de batería). **A partir de esta fase
significa algo más serio: un stream RTSP real puede quedarse emitiendo la
cámara hacia un servidor externo de fondo, después de que el usuario cierre
el panel, mientras el plugin siga cargado.** No se arregla aquí — arreglarlo
bien requiere investigar la API de visibilidad de `Pane`/`IHostUIService`
(sin explorar todavía, y esta fase ya tiene su propio alcance concreto:
verificar el push RTSP en sí). **Queda anotado como la primera tarea a
decidir explícitamente antes o al principio de la Fase 3** (emisión CoT) —
no se pierde de vista, pero tampoco se cuela sin pedirlo en el alcance de
esta fase.

**Tech Stack:** RootEncoder v2.8.1 (ya integrado en la Fase 1) —
`prepareVideo()`/`prepareAudio()`/`startStream()`/`stopStream()`/`isStreaming()`
de `Camera2Base`. [MediaMTX](https://github.com/bluenviron/mediamtx) como
receptor RTSP desechable solo para verificar esta fase (no es parte del
repo ni del plugin).

## Global Constraints

- Todo lo de `icu_ng_atak` (emulador) y `tools/atak-plugin-dev` (contenedor)
  de las fases 0-1 aplica igual — ver
  [2026-09-17-icu-ng-phase0-skeleton.md](2026-09-17-icu-ng-phase0-skeleton.md)
  y [2026-09-19-icu-ng-phase1-camera-preview.md](2026-09-19-icu-ng-phase1-camera-preview.md)
  Global Constraints. Docker Desktop y el emulador rara vez sobreviven entre
  sesiones — comprobar `doctor` primero siempre.
- `prepareVideo()`/`prepareAudio()` (sin argumentos) usan los valores por
  defecto de RootEncoder: vídeo 640×480 @30fps / 1200 kbps, audio 64 kbps /
  32000 Hz / estéreo. No se ajustan en esta fase — es el mínimo para
  verificar que el push funciona de extremo a extremo; calidad/resolución
  configurable queda para más adelante si hace falta.
- **`prepareVideo()`/`prepareAudio()` tienen que haberse llamado antes de
  `startStream()`, o revienta.** Verificado contra el código: `startEncoders()`
  llama a `videoEncoder.start(...)` directamente, sin comprobar que se haya
  preparado antes — RootEncoder no protege esto, es responsabilidad nuestra
  (documentado en el propio Javadoc del método: *"Need be called after
  @prepareVideo or/and @prepareAudio"*).
- Sin autenticación ni cifrado (RTSPS) en esta fase — RTSP plano, como marca
  el orden decidido en la spec de diseño general ("RTSP/RTSPS básico...
  antes que SRT/multicast", empezando por lo más simple).
- El campo de URL por defecto usa `10.0.2.2` — el alias fijo que usa el
  emulador de Android para referirse al `localhost` del host, así que un
  MediaMTX corriendo en la máquina de desarrollo (Task 3) es alcanzable sin
  configurar IPs a mano.
- Permisos nuevos en el manifest: `RECORD_AUDIO` e `INTERNET` — por la misma
  razón que `CAMERA` en la Fase 1 (el código del plugin corre en el proceso
  de ATAK, hereda sus permisos; declararlos en nuestro manifest es
  documentación/higiene, no lo que hace que funcione en tiempo de
  ejecución). Task 3 lo verifica en el dispositivo, no se da por sentado.

## File Structure

```
icu-replacement/
└── app/src/main/
    ├── AndroidManifest.xml                 (modificar: permisos RECORD_AUDIO, INTERNET)
    ├── res/
    │   ├── layout/main_layout.xml          (modificar: campo de URL + botón, superpuestos al preview)
    │   └── values/strings.xml              (modificar: textos del botón)
    └── java/com/carlosgt/atak/icung/plugin/
        └── IcuNgPlugin.java                (modificar: prepareVideo/prepareAudio, toggleStream(), onStop() ampliado)
```

Ningún fichero nuevo — todo sobre lo que dejaron las fases 0 y 1.

## Task 1: Permisos, layout y textos del control de streaming

**Files:**
- Modify: `icu-replacement/app/src/main/AndroidManifest.xml`
- Modify: `icu-replacement/app/src/main/res/layout/main_layout.xml`
- Modify: `icu-replacement/app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `@+id/rtsp_url_input` (`EditText`) y `@+id/stream_toggle_button`
  (`Button`) en `main_layout.xml` — la Tarea 2 hace `findViewById` sobre
  estos dos ids. `@string/start_stream` / `@string/stop_stream` — la Tarea 2
  los usa para el texto del botón según `camera.isStreaming()`.

- [ ] **Step 1: Añadir los permisos que faltan**

En `icu-replacement/app/src/main/AndroidManifest.xml`, junto al permiso
`CAMERA` que ya añadió la Fase 1:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Añadir los textos del botón**

En `icu-replacement/app/src/main/res/values/strings.xml`, junto a
`app_name`/`app_desc` que ya existen:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- App -->
    <string name="app_name">ICU NG</string>
    <!-- description -->
    <string name="app_desc">Sustituto de TAK ICU: streaming de cámara con audio, anunciado en el mapa vía CoT.</string>

    <string name="start_stream">Emitir</string>
    <string name="stop_stream">Detener</string>
</resources>
```

- [ ] **Step 3: Añadir el campo de URL y el botón, superpuestos al preview**

Reemplazar el contenido de
`icu-replacement/app/src/main/res/layout/main_layout.xml` (la `OpenGlView`
de la Fase 1 se queda exactamente igual, solo se añade la barra inferior):

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
        android:orientation="horizontal"
        android:background="#80000000"
        android:padding="4dp">

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

</FrameLayout>
```

- [ ] **Step 4: Verificar que compila**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Todavía no hay código Java tocando los ids
nuevos, así que si falla aquí es casi seguro un error de XML (etiqueta sin
cerrar, id repetido).

- [ ] **Step 5: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/AndroidManifest.xml icu-replacement/app/src/main/res/layout/main_layout.xml icu-replacement/app/src/main/res/values/strings.xml
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): permisos y UI del control de streaming RTSP

Prepara el panel para la Fase 2 (push RTSP): permisos RECORD_AUDIO e
INTERNET en el manifest, y un campo de URL + boton Emitir/Detener
superpuestos al preview de camara de la Fase 1 (sin tocar la OpenGlView
que ya funciona). Sin logica de streaming todavia - eso es la Tarea 2.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 2: Conectar startStream()/stopStream() al botón y a onStop()

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `@+id/rtsp_url_input`, `@+id/stream_toggle_button` (Tarea 1);
  `camera` (`RtspCamera2`, Fase 1) — mismo campo, sin uno nuevo.
- Produces: nada expuesto fuera de la clase — `toggleStream()` es privado,
  simétrico a `showPane()`.

- [ ] **Step 1: Añadir los imports que faltan**

Junto a los imports de RootEncoder que ya añadió la Fase 1:

```java
import android.widget.Button;
import android.widget.EditText;
```

- [ ] **Step 2: Añadir los campos de la UI de streaming**

Junto a `camera` (Fase 1):

```java
    RtspCamera2 camera;
    EditText rtspUrlInput;
    Button streamToggleButton;
```

- [ ] **Step 3: Preparar vídeo/audio al construir la cámara**

Dentro del bloque `if(templatePane == null)` de `showPane()`, justo después
de la línea `camera = new RtspCamera2(previewView, this);` que ya existe
(antes de registrar el `SurfaceHolder.Callback` de la Fase 1 o después, da
igual el orden entre estas dos líneas — ninguna toca el `Surface`):

```java
            camera = new RtspCamera2(previewView, this);
            camera.prepareVideo();
            camera.prepareAudio();
```

- [ ] **Step 4: Enlazar el campo de URL y el botón**

En el mismo bloque `if(templatePane == null)`, después de inflar `paneView`
(el orden exacto respecto al resto no importa, pero tiene que ser después de
`View paneView = PluginLayoutInflater.inflate(...)`):

```java
            rtspUrlInput = paneView.findViewById(R.id.rtsp_url_input);
            streamToggleButton = paneView.findViewById(R.id.stream_toggle_button);
            streamToggleButton.setOnClickListener(v -> toggleStream());
```

- [ ] **Step 5: Añadir el método `toggleStream()`**

Añadirlo como método privado de la clase, junto a `showPane()`:

```java
    private void toggleStream() {
        if (camera.isStreaming()) {
            camera.stopStream();
            streamToggleButton.setText(R.string.start_stream);
        } else {
            String url = rtspUrlInput.getText().toString();
            camera.startStream(url);
            streamToggleButton.setText(R.string.stop_stream);
        }
    }
```

- [ ] **Step 6: Parar el stream en `onStop()`, antes del preview**

Reemplazar el método `onStop()` completo por (añade el `stopStream()` antes
del `stopPreview()` que ya existía en la Fase 1 — ver Decisión 2 arriba
sobre por qué el orden es así y por qué no hace falta nada más):

```java
    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);

        if (camera != null && camera.isStreaming()) {
            camera.stopStream();
        }

        if (camera != null && camera.isOnPreview()) {
            camera.stopPreview();
        }
    }
```

- [ ] **Step 7: Compilar**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Si falla con `cannot find symbol: variable
rtspUrlInput` o similar, revisar que el Step 4 quedó dentro del mismo
bloque `if(templatePane == null)` que declara `paneView`.

- [ ] **Step 8: `scan` y commit**

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
feat(icu-replacement): push RTSP real sobre la camara ya en preview

toggleStream() llama a startStream(url)/stopStream() sobre la misma
instancia de RtspCamera2 que la Fase 1 ya usa para el preview -
prepareVideo()/prepareAudio() se preparan una vez al construir la
camara. onStop() para el stream antes que el preview, reutilizando el
mismo hook de la Fase 1 en vez de un ciclo de vida paralelo.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 3: Deploy y verificación contra un receptor RTSP real

**Files:** ninguno (verifica en el dispositivo, contra un receptor externo,
lo que dejaron las Tareas 1 y 2).

**Interfaces:**
- Consumes: APK de `icu-replacement` con `toggleStream()` funcionando
  (Tareas 1-2), y un MediaMTX corriendo en el host como receptor de prueba
  (no es parte de este repo).

- [ ] **Step 1: Confirmar que el entorno sigue sano**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev doctor
```

Si falla, seguir la secuencia de arreglo de las fases 0-1 (Docker Desktop,
`adb -a -P 5037 server nodaemon`, arrancar `icu_ng_atak`) antes de seguir.

- [ ] **Step 2: Levantar un MediaMTX desechable en el host, como receptor de prueba**

```powershell
docker run --rm -d --name icung-test-mediamtx -p 8554:8554 bluenviron/mediamtx
```

Expected: el contenedor arranca y queda escuchando en `8554`. Esto **no**
es parte del repo ni de `tools/atak-plugin-dev` — es infraestructura de
prueba desechable, solo para esta verificación (ver Decisión 1 arriba: el
servidor receptor está fuera de alcance del subproyecto).

- [ ] **Step 3: Build + install + stage**

```powershell
docker compose exec atak-dev deploy icu-replacement
```

Expected: `BUILD SUCCESSFUL`, `Performing Streamed Install` → `Success`.

- [ ] **Step 4: Sync + Load en ATAK**

Igual que en las fases 0-1: hamburguesa (Tools) → vista de lista → **Plugins**
→ **Sync Now** → OK → fila **ICU NG** → **Load** → OK (o confirmar que ya
aparece `Loaded`/`Current` tras el sync, como pasó en la Fase 1 — ATAK
recarga automáticamente si el paquete ya estaba cargado).

- [ ] **Step 5: Abrir el panel y confirmar que el preview sigue funcionando**

Abrir el panel de ICU NG (Tools → lista → ICU NG).

Expected: se ve el preview de cámara de la Fase 1 (el patrón sintético de
la cámara emulada), y ahora además la barra inferior con el campo de URL
(precargado con `rtsp://10.0.2.2:8554/icung`) y el botón "Emitir". Esto
confirma que añadir la UI de streaming no rompió el preview que ya
funcionaba.

- [ ] **Step 6: Pulsar "Emitir" y confirmar el push en el log de MediaMTX**

Tocar el botón "Emitir" en el panel. Debería cambiar a "Detener".

```powershell
docker logs icung-test-mediamtx --tail 20
```

Expected: una línea de MediaMTX confirmando que un publisher se conectó al
path `icung` (el formato exacto de la línea lo confirma la propia
ejecución — MediaMTX deja claro en su log cuando un cliente empieza a
publicar en un path). Si no aparece nada, revisar primero
`adb logcat -d | grep -i icung` en busca de un fallo de conexión
(`onConnectionFailed` de `ConnectChecker`, todavía sin usar más que como
no-op — este es el primer momento en que un fallo real pasaría por ahí sin
que lo veamos en pantalla, solo en el log).

- [ ] **Step 7: Verificación por log — sin `SecurityException` de audio/red**

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep -i icung' 2>&1 | Select-String -Pattern "SecurityException|CameraAccessException" 
```

Expected: sin resultados — confirma que la hipótesis de herencia de
permisos (Fase 1, ahora extendida a `RECORD_AUDIO`/`INTERNET`) se sostiene
también para micrófono y red. Si aparece algo, el plan B es el mismo que
documentó la Fase 1: una `PluginPermissionActivity` propia.

- [ ] **Step 8: Pulsar "Detener" y confirmar que el preview sigue vivo**

Tocar "Detener" en el panel. Debería volver a "Emitir".

Expected: el preview de cámara **sigue viéndose** (no se congela ni
desaparece) — confirma en el dispositivo real lo que la Decisión 2 predijo
por código: `stopStream()` no cierra la cámara cuando se usa `OpenGlView`.

- [ ] **Step 9: Limpiar el receptor de prueba**

```powershell
docker stop icung-test-mediamtx
```

Sin commit en esta tarea — solo verificación en el dispositivo y contra un
receptor externo de lo que dejaron las Tareas 1 y 2.

---

## Self-Review

- **Cobertura de la Fase 2 del diseño** ("Servidor/push RTSP —
  configuración de servidor destino, start/stop real hacia un receptor,
  probado contra `tak-video-restreamer` o MediaMTX standalone. Hecho = un
  cliente RTSP externo puede reproducir el stream"): cubierto — Task 1 la
  UI de configuración/control, Task 2 el `startStream`/`stopStream` real,
  Task 3 la verificación contra un MediaMTX real (el log de "publisher
  conectado" es equivalente a "un cliente externo puede reproducirlo": si
  MediaMTX lo recibe, cualquier cliente RTSP puede pedírselo).
- **Placeholders:** ninguno — `startStream`, `stopStream`, `prepareVideo`,
  `prepareAudio`, `isStreaming` y el motivo exacto por el que `stopStream()`
  no cierra la cámara con `OpenGlView`, todo citado contra el código fuente
  real de `Camera2Base.java`, no de memoria.
- **Las tres decisiones que pedías explícitas:** están como su propia
  sección dentro de Architecture, cada una con la evidencia que la
  respalda — no son afirmaciones sueltas.
- **Consistencia de nombres:** `camera` (mismo campo de la Fase 1),
  `rtspUrlInput`/`streamToggleButton` usados igual en Task 1 (ids del
  layout) y Task 2 (Java). `toggleStream()` es la única función nueva que
  toca streaming.
- **Fuera de alcance, deliberadamente:** RTSPS/RTMP/SRT/multicast,
  autenticación/cifrado, resolución/bitrate configurable, persistencia de
  la URL entre reinicios de ATAK, y — la más importante para no perderla de
  vista — **arreglar que el stream (no solo la cámara) se quede vivo de
  fondo al ocultar el panel**, marcado explícitamente como la primera
  decisión a tomar en la Fase 3.
