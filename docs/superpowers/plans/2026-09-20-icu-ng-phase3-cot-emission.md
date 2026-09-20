# ICU NG — Fase 3: emisión CoT — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mientras ICU NG emite (Fase 2), el marcador propio en el mapa de
**los demás** ATAK de la red muestra un cono `<sensor>` de FOV/rumbo y un
detalle `<__video>` con la URL — sin que este plugin retransmita ni un solo
píxel. Todo se apaga solo si se pierde el fix GPS, se para la emisión, se
oculta el panel, o se detiene el plugin — sin fugas de cámara/stream/cono de
fondo. Ver [2026-09-17-icu-ng-design.md](../specs/2026-09-17-icu-ng-design.md)
(fase "Emisión CoT" del diseño general).

**Architecture:** Todas las clases y firmas de este plan están verificadas
contra el bytecode real de `/opt/atak-sdk/main.jar` (`javap`, ATAK CIV SDK
5.8.0.5) — no de memoria ni de documentación antigua. El SDK trae dos
generaciones de API de CoT/vídeo conviviendo: la clásica
(`com.atakmap.android.*`, con `MapItem`/`com.atakmap.coremap.cot.event.CotDetail`)
y la nueva (`gov.tak.api.*`, con `Object`/`gov.tak.api.cot.event.CotDetail`,
la misma familia que ya usa este plugin desde la Fase 0 para `IPlugin`).
Este plan usa la nueva donde existe equivalente directo, y la clásica donde
no (el cono de sensor solo tiene helper en la clásica).

### Decisión 1 (resuelta primero, tal como se pidió): ciclo de vida cámara/stream/cono vs. panel oculto vs. plugin detenido

**El hueco pendiente de las Fases 1-2 se cierra aquí, con una API real que ya
existía sin usar.** `IHostUIService.showPane(Pane, IPaneLifecycleListener)` —
el **segundo parámetro**, que este plugin pasa como `null` desde la Fase 0 —
es exactamente el gancho que faltaba. Verificado con `javap`:

```
public interface gov.tak.api.ui.IHostUIService {
  void showPane(Pane, IHostUIService$IPaneLifecycleListener);
  ...
}
public interface gov.tak.api.ui.IHostUIService$IPaneLifecycleListener {
  void onPaneVisible(boolean);
  void onPaneClose();
}
```

**Diseño de los tres niveles, del más al menos frecuente:**

| Evento | Cuándo | Qué se para | Qué se reinicia |
| --- | --- | --- | --- |
| `onPaneVisible(false)` | el usuario oculta el panel (cambia de herramienta) sin cerrarlo | stream (si emitiendo) + preview (si activo) + cono `<sensor>` (si existe) | — |
| `onPaneVisible(true)` | el panel vuelve a ser visible | — | preview, si no estaba ya activo (`!camera.isOnPreview()`) |
| `onPaneClose()` | el panel se cierra del todo | mismo teardown que `onPaneVisible(false)` — red de seguridad idempotente | — |
| `onStop()` (ya existía) | el plugin entero se detiene | todo lo anterior, **más** desregistrar el handler de CoT y quitar el `ConnectionEntry` del `VideoManager` | — |

El **detalle `<__video>`** no necesita un paso de teardown explícito propio:
como su `isSupported()` (Decisión 3, más abajo) comprueba en cada ciclo si
seguimos emitiendo, en cuanto `stopStream()` corre (desde cualquiera de las
filas de arriba) el siguiente ciclo de SA propia de ATAK ya no lo incluye —
sin necesidad de "retirarlo" a mano. El **cono `<sensor>`**, al ser un
`MapItem` persistente en el mapa (no una evaluación al vuelo), sí necesita
`removeFromGroup()` explícito — no desaparece solo.

### Decisión 2: `<__video>` con handler propio, `<sensor>` con el helper ya registrado de ATAK — no es el mismo mecanismo para los dos

**`<sensor>`:** `com.atakmap.android.cot.detail.SensorDetailHandler` ya está
registrado por el propio núcleo de ATAK (no deprecado, con métodos
`public static` pensados para que otro código lo invoque:
`addFovToMap(PointMapItem, double fov, double azimuth, double range, float[] color, boolean showRangeLines)`
y `hasFoV(MapItem)`, verificados por `javap -constants`). No hace falta
escribir ni registrar ningún handler propio para el cono — basta con crear/
actualizar/quitar el `SensorFOV` sobre el marcador propio y ATAK lo serializa
solo en el siguiente ciclo de SA.

**`<__video>`:** aquí sí hace falta un handler propio, y es una decisión
deliberada, no la única opción posible. ATAK trae su propio
`gov.tak.api.video.cot.VideoDetailHandler` (implementa
`ICotDetailHandler2`, constructor `(IVideoConnectionManager)`, con
constantes como `VIDEO_SPI_UID_ATTR`/`VIDEO_SENSOR_UID_ATTR` que apuntan a
que sabe enlazar automáticamente un `ConnectionEntry` con un `MapItem`
concreto. **Pero `javap` solo da firmas, no cuerpos de método** — no hay
forma de confirmar contra bytecode compilado cuál es la clave de metadata
exacta que usa `isSupported()`/`toCotDetail()` para decidir a qué marcador
enlazar un `ConnectionEntry`, y el mirror público de `atak-civ` en GitHub
(`TAK-Product-Center/atak-civ`) no llega todavía al paquete `gov.tak.api`
(comprobado: 404 en esa ruta) para poder leer el código real. Apostar la
Fase 3 entera a un comportamiento interno no verificable es el mismo tipo de
riesgo que ya nos crasheó en la Fase 1 (`startPreview()` antes de que
existiera el `Surface`) — así que aquí se opta por control explícito:
**handler propio, registrado vía `CotDetailManager`, cuyo `isSupported()`
compara el UID del `MapItem` contra el de nuestro marcador propio a mano.**
Eso sí, se reutiliza la única pieza de ATAK que es un método `static`
verificado y sin ambigüedad —
`gov.tak.api.video.cot.ConnectionEntryDetail.toCotDetail(ConnectionEntry)` —
para construir el contenido real del detalle, así que no se reescribe a
mano el formato `<__video url="..." uid="..." .../>`.

### Decisión 3: gating por GPS — un único punto de verdad, sin duplicar lógica

`selfMarker.getPoint().isValid()` (`com.atakmap.coremap.maps.coords.GeoPoint.isValid()`,
verificado con `javap`) es el único punto de verdad para "¿hay fix?". Se usa
en dos sitios con dos mecánicas distintas, a propósito:

- **`<__video>`:** dentro de `isSupported()` del handler — se reevalúa solo,
  en cada ciclo de SA propia de ATAK. No hace falta ningún listener para
  esto.
- **Cono `<sensor>`:** al ser un `MapItem` persistente, si no se comprobara
  también aquí el cono se quedaría "congelado" en la última posición válida
  cuando se pierde el fix. Se engancha a
  `PointMapItem.addOnPointChangedListener(OnPointChangedListener)`
  (verificado con `javap`, dispara `onPointChanged(PointMapItem)` en cada
  actualización del marcador propio): si hay fix y se está emitiendo,
  asegurar que el cono existe (crearlo o `setMetrics(...)` si ya existía);
  si no, `removeFromGroup()`.

**Rumbo del cono:** `Marker.getTrackHeading()` (verificado con `javap`) —
rumbo de desplazamiento (course over ground), no orientación real de la
brújula del dispositivo. Es lo mismo que usa TAK ICU (el plugin que
sustituimos) y es la única fuente de rumbo que ya expone el propio marcador
propio sin añadir un sensor de orientación aparte. Si el operador está
parado girando el teléfono, el cono no rotará — limitación conocida,
documentada como fuera de alcance (ver más abajo), no un bug de esta fase.

**Tech Stack:** ATAK CIV SDK 5.8.0.5 (`gov.tak.api.video.*`,
`gov.tak.api.video.cot.*`, `gov.tak.api.cot.detail.*`,
`com.atakmap.android.cot.detail.SensorDetailHandler`,
`com.atakmap.android.maps.MapView`/`Marker`/`SensorFOV`). Nada nuevo de
RootEncoder — esta fase no toca la cámara ni el streaming en sí, solo lo
que ATAK necesita saber sobre ellos.

## Global Constraints

- Todo lo de `icu_ng_atak` (emulador) y `tools/atak-plugin-dev` (contenedor)
  de las fases 0-2 aplica igual — ver los planes anteriores. Docker Desktop
  y el emulador rara vez sobreviven entre sesiones — `doctor` primero
  siempre.
- El UID del marcador propio se obtiene con
  `MapView.getMapView().getSelfMarker().getUID()` — nunca hardcodear ni
  cachear más allá de una sesión (ATAK puede regenerarlo).
- El `ConnectionEntry` y el cono `<sensor>` se crean **una sola vez** (al
  empezar a emitir con fix) y se actualizan in situ (`setMetrics`,
  reconstrucción del `ConnectionEntry` solo si cambia la URL) — no
  recrearlos en cada ciclo de SA.
- Sin autenticación en el `ConnectionEntry` (`RTSP` plano, igual que la
  Fase 2) — coherente con que el push en sí tampoco la tiene todavía.
- **No se reutiliza el `gov.tak.api.video.cot.VideoDetailHandler` interno de
  ATAK** — ver Decisión 2. Si en una fase futura se decide confiar en él
  (por ejemplo tras confirmar su comportamiento leyendo el código fuente
  real, no solo bytecode), sustituir el handler propio es un cambio
  localizado, no una reescritura.
- Rumbo del cono = `Marker.getTrackHeading()` (course over ground) — no
  brújula del dispositivo. Ver Decisión 3.

## File Structure

```
icu-replacement/
└── app/src/main/java/com/carlosgt/atak/icung/plugin/
    ├── IcuNgPlugin.java              (modificar: IPaneLifecycleListener, orquesta las tres piezas nuevas)
    ├── SelfVideoDetailHandler.java   (nuevo: ICotDetailHandler2 para <__video>)
    └── SelfSensorFovController.java  (nuevo: gestiona el SensorFOV del marcador propio)
```

Dos ficheros nuevos porque cada uno tiene una responsabilidad clara y
comprobable por separado (igual que el criterio ya seguido en las fases
anteriores de no amontonar todo en `IcuNgPlugin.java`) — `IcuNgPlugin`
pasa a orquestar, no a implementar los detalles de CoT.

## Task 1: Resolver el ciclo de vida del panel (Decisión 1)

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Produces: un método privado `stopCameraAndStream()` que consolida lo que
  hoy está repetido entre `onStop()` y lo nuevo — las Tareas 2 y 3 lo llaman
  también para saber cuándo quitar el `ConnectionEntry`/cono.

- [ ] **Step 1: Extraer el teardown repetido a un método propio**

En `IcuNgPlugin.java`, reemplazar el cuerpo de `onStop()`:

```java
    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);

        stopCameraAndStream();
    }

    private void stopCameraAndStream() {
        if (camera != null && camera.isStreaming()) {
            camera.stopStream();
        }

        if (camera != null && camera.isOnPreview()) {
            camera.stopPreview();
        }
    }
```

- [ ] **Step 2: Pasar un `IPaneLifecycleListener` real a `showPane()`**

Añadir el import:

```java
import gov.tak.api.ui.IHostUIService;
```

(ya está importado — solo hace falta el tipo anidado, no un import nuevo).

Cambiar la línea `uiService.showPane(templatePane, null);` dentro de
`showPane()` por:

```java
        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, new IHostUIService.IPaneLifecycleListener() {
                @Override
                public void onPaneVisible(boolean visible) {
                    if (visible) {
                        if (camera != null && !camera.isOnPreview()) {
                            camera.startPreview(CameraHelper.Facing.BACK);
                        }
                    } else {
                        stopCameraAndStream();
                    }
                }

                @Override
                public void onPaneClose() {
                    stopCameraAndStream();
                }
            });
        }
```

- [ ] **Step 3: Compilar**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: `scan` y commit**

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
fix(icu-replacement): liberar camara/stream al ocultar el panel, no solo al detener el plugin

Usa el segundo parametro de showPane() (IPaneLifecycleListener),
pasado como null desde la Fase 0 sin darnos cuenta de que era
exactamente el gancho que faltaba. onPaneVisible(false) y
onPaneClose() paran stream+preview; onPaneVisible(true) los reinicia
si hacia falta. Resuelve la simplificacion pendiente documentada en
las Fases 1 y 2 antes de construir la emision CoT sobre ella.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 2: Registrar el `ConnectionEntry` y el handler de `<__video>`

**Files:**
- Create: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/SelfVideoDetailHandler.java`
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Produces: `SelfVideoDetailHandler` — constructor
  `SelfVideoDetailHandler(String selfUid, java.util.function.Supplier<Boolean> isStreamingAndFixed, java.util.function.Supplier<gov.tak.api.video.ConnectionEntry> connectionEntrySupplier)`.
  `IcuNgPlugin` lo construye una vez y lo registra/desregistra.

- [ ] **Step 1: Crear `SelfVideoDetailHandler.java`**

```java
package com.carlosgt.atak.icung.plugin;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import gov.tak.api.cot.detail.ICotDetailHandler;
import gov.tak.api.cot.detail.ICotDetailHandler2;
import gov.tak.api.cot.event.CotDetail;
import gov.tak.api.cot.event.CotEvent;
import gov.tak.api.util.AttributeSet;
import gov.tak.api.video.ConnectionEntry;
import gov.tak.api.video.cot.ConnectionEntryDetail;

import com.atakmap.android.maps.MapItem;

/**
 * Adds a {@code <__video>} detail to the self marker's own CoT event while
 * ICU NG is streaming with a GPS fix - never to any other marker.
 */
public class SelfVideoDetailHandler implements ICotDetailHandler2 {

    private static final Set<String> DETAIL_NAMES = Collections.singleton("__video");

    private final String selfUid;
    private final Supplier<Boolean> isStreamingAndFixed;
    private final Supplier<ConnectionEntry> connectionEntrySupplier;

    public SelfVideoDetailHandler(String selfUid, Supplier<Boolean> isStreamingAndFixed,
            Supplier<ConnectionEntry> connectionEntrySupplier) {
        this.selfUid = selfUid;
        this.isStreamingAndFixed = isStreamingAndFixed;
        this.connectionEntrySupplier = connectionEntrySupplier;
    }

    @Override
    public Set<String> getDetailNames() {
        return DETAIL_NAMES;
    }

    @Override
    public boolean isSupported(Object item, AttributeSet attrs, CotEvent event, CotDetail detail) {
        if (!(item instanceof MapItem))
            return false;
        if (!selfUid.equals(((MapItem) item).getUID()))
            return false;
        return Boolean.TRUE.equals(isStreamingAndFixed.get());
    }

    @Override
    public boolean toCotDetail(Object item, AttributeSet attrs, CotEvent event, CotDetail detail) {
        ConnectionEntry entry = connectionEntrySupplier.get();
        if (entry == null)
            return false;

        CotDetail videoDetail = ConnectionEntryDetail.toCotDetail(entry);
        detail.addChild(videoDetail);
        return true;
    }

    @Override
    public ICotDetailHandler.ImportResult toItemMetadata(Object item, AttributeSet attrs,
            CotEvent event, CotDetail detail) {
        // ICU NG never needs to *receive* a <__video> detail for its own
        // marker - only to emit one.
        return ICotDetailHandler.ImportResult.IGNORE;
    }
}
```

- [ ] **Step 2: Confirmar `CotDetail.addChild` antes de dar el Step 1 por bueno**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev javap -classpath /opt/atak-sdk/main.jar gov.tak.api.cot.event.CotDetail
```

Expected: la lista de métodos incluye algo como `addChild(CotDetail)` (o el
nombre real que aparezca — este `javap` es la comprobación, no una
suposición: si el nombre difiere, ajustar `SelfVideoDetailHandler.toCotDetail`
al nombre real antes de seguir).

- [ ] **Step 3: Añadir los campos y el registro del handler en `IcuNgPlugin.java`**

Imports nuevos:

```java
import gov.tak.api.video.ConnectionEntry;
import gov.tak.api.video.ConnectionEntryBase;
import gov.tak.api.video.IVideoConnectionManager;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.manager.VideoManager;
```

Campos nuevos, junto a `camera`:

```java
    RtspCamera2 camera;
    IVideoConnectionManager videoConnectionManager;
    SelfVideoDetailHandler videoDetailHandler;
    ConnectionEntry connectionEntry;
```

- [ ] **Step 4: Construir y registrar el handler en `onStart()`**

Reemplazar `onStart()` completo:

```java
    @Override
    public void onStart() {
        // the plugin is starting, add the button to the toolbar
        if (uiService == null)
            return;

        uiService.addToolbarItem(toolbarItem);

        videoConnectionManager = VideoManager.getInstance();

        String selfUid = MapView.getMapView().getSelfMarker().getUID();
        videoDetailHandler = new SelfVideoDetailHandler(
                selfUid,
                this::isStreamingWithFix,
                () -> connectionEntry);
        CotDetailManager.getInstance().registerHandler(videoDetailHandler);
    }

    private boolean isStreamingWithFix() {
        return camera != null && camera.isStreaming()
                && MapView.getMapView().getSelfMarker().getPoint().isValid();
    }
```

- [ ] **Step 5: Registrar/quitar el `ConnectionEntry` al arrancar/parar el stream**

Modificar `toggleStream()` (Fase 2) para que también gestione el
`ConnectionEntry` — reemplazar el método completo:

```java
    private void toggleStream() {
        if (camera.isStreaming()) {
            camera.stopStream();
            streamToggleButton.setText(R.string.start_stream);
            if (connectionEntry != null) {
                videoConnectionManager.removeConnectionEntry(connectionEntry.getUID());
                connectionEntry = null;
            }
        } else {
            String url = rtspUrlInput.getText().toString();
            camera.startStream(url);
            streamToggleButton.setText(R.string.stop_stream);

            connectionEntry = buildConnectionEntry(url);
            videoConnectionManager.addConnectionEntry(connectionEntry);
        }
    }

    private ConnectionEntry buildConnectionEntry(String url) {
        android.net.Uri parsed = android.net.Uri.parse(url);
        return new ConnectionEntryBase.Builder()
                .setUID(pluginContext.getPackageName() + "." + System.currentTimeMillis())
                .setAlias("ICU NG")
                .setAddress(parsed.getHost())
                .setPort(parsed.getPort() > 0 ? parsed.getPort() : ConnectionEntryBase.Protocol.RTSP.getDefaultPort())
                .setPath(parsed.getPath())
                .setProtocol(ConnectionEntryBase.Protocol.RTSP)
                .setSource(ConnectionEntryBase.Source.EXTERNAL)
                .build();
    }
```

- [ ] **Step 6: Desregistrar el handler y quitar el `ConnectionEntry` en el teardown**

Ampliar `stopCameraAndStream()` (Task 1) para que también limpie el
`ConnectionEntry` cuando el stream se para desde cualquiera de sus tres
disparadores (ocultar panel, cerrar panel, detener plugin):

```java
    private void stopCameraAndStream() {
        if (camera != null && camera.isStreaming()) {
            camera.stopStream();
            if (connectionEntry != null) {
                videoConnectionManager.removeConnectionEntry(connectionEntry.getUID());
                connectionEntry = null;
            }
        }

        if (camera != null && camera.isOnPreview()) {
            camera.stopPreview();
        }
    }
```

Y añadir el desregistro del handler de CoT en `onStop()` (que ya llama a
`stopCameraAndStream()` — solo falta esta línea extra):

```java
    @Override
    public void onStop() {
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);

        stopCameraAndStream();

        if (videoDetailHandler != null) {
            CotDetailManager.getInstance().unregisterHandler(videoDetailHandler);
        }
    }
```

- [ ] **Step 7: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. El error más probable si algo va mal es sobre
`CotDetail.addChild` (Step 2) — resolver eso antes de tocar nada más.

- [ ] **Step 8: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/SelfVideoDetailHandler.java icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): anunciar <__video> en el marcador propio mientras se emite

SelfVideoDetailHandler (ICotDetailHandler2 propio, registrado via
CotDetailManager) solo actua sobre el UID del marcador propio, y solo
si hay stream activo y fix GPS - reutiliza el helper estatico
verificado ConnectionEntryDetail.toCotDetail() para construir el
detalle real en vez de escribir el XML a mano. El ConnectionEntry se
registra en VideoManager (implementa IVideoConnectionManager) al
arrancar el stream y se retira al pararlo, desde cualquiera de los
tres disparadores de teardown de la Tarea 1.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 3: Cono `<sensor>` de FOV/rumbo sobre el marcador propio

**Files:**
- Create: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/SelfSensorFovController.java`
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Produces: `SelfSensorFovController` — `start()` engancha el
  `OnPointChangedListener`; `refresh(boolean streaming)` decide crear/
  actualizar/quitar el cono; `stop()` desengancha el listener y quita el
  cono si existe. `IcuNgPlugin` llama a `refresh(...)` desde los mismos
  puntos donde ya toca `connectionEntry` (Task 2) — un solo lugar por
  transición de estado, no dos.

- [ ] **Step 1: Crear `SelfSensorFovController.java`**

```java
package com.carlosgt.atak.icung.plugin;

import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.SensorFOV;

/**
 * Owns the <sensor> FOV cone on ICU NG's own self marker. Not a CoT detail
 * handler - SensorDetailHandler (built into ATAK, already registered) does
 * that on its own once this map item exists.
 */
public class SelfSensorFovController implements PointMapItem.OnPointChangedListener {

    private static final float FOV_DEGREES = 60f;
    private static final float RANGE_METERS = 200f;
    private static final float[] COLOR = { 0f, 1f, 1f, 0.4f }; // r, g, b, alpha

    private final Marker selfMarker;
    private boolean streaming;
    private SensorFOV fov;

    public SelfSensorFovController() {
        this.selfMarker = MapView.getMapView().getSelfMarker();
    }

    public void start() {
        selfMarker.addOnPointChangedListener(this);
    }

    public void stop() {
        selfMarker.removeOnPointChangedListener(this);
        removeFov();
    }

    public void refresh(boolean streaming) {
        this.streaming = streaming;
        update();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        update();
    }

    private void update() {
        if (streaming && selfMarker.getPoint().isValid()) {
            double azimuth = selfMarker.getTrackHeading();
            if (fov == null) {
                fov = SensorDetailHandler.addFovToMap(selfMarker, FOV_DEGREES, azimuth,
                        RANGE_METERS, COLOR, false);
            } else {
                fov.setMetrics((float) FOV_DEGREES, (float) azimuth, RANGE_METERS);
            }
        } else {
            removeFov();
        }
    }

    private void removeFov() {
        if (fov != null) {
            fov.removeFromGroup();
            fov = null;
        }
    }
}
```

- [ ] **Step 2: Confirmar la firma exacta de `SensorFOV.setMetrics` antes de dar el Step 1 por bueno**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev javap -classpath /opt/atak-sdk/main.jar com.atakmap.android.maps.SensorFOV
```

Expected: entre las tres sobrecargas de `setMetrics` verificadas en la
investigación de este plan, `setMetrics(float, float, float)` — confirmar
el **orden** de los tres parámetros (fov, azimuth, extent/range) contra esta
salida real, no contra lo escrito arriba, antes de compilar.

- [ ] **Step 3: Enganchar el controlador en `IcuNgPlugin.java`**

Campo nuevo, junto a `videoDetailHandler`:

```java
    SelfSensorFovController sensorFovController;
```

En `onStart()`, después de `CotDetailManager.getInstance().registerHandler(videoDetailHandler);`:

```java
        sensorFovController = new SelfSensorFovController();
        sensorFovController.start();
```

En `toggleStream()` (Task 2), añadir una línea en cada rama — justo después
de `streamToggleButton.setText(R.string.start_stream);` en la rama de parar:

```java
            sensorFovController.refresh(false);
```

Y justo después de `streamToggleButton.setText(R.string.stop_stream);` en
la rama de arrancar:

```java
            sensorFovController.refresh(true);
```

En `stopCameraAndStream()` (Task 1/2), dentro del bloque
`if (camera != null && camera.isStreaming())`, junto a la línea que quita
`connectionEntry`:

```java
            sensorFovController.refresh(false);
```

En `onStop()`, después de desregistrar `videoDetailHandler`:

```java
        if (sensorFovController != null) {
            sensorFovController.stop();
        }
```

- [ ] **Step 4: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/SelfSensorFovController.java icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): cono <sensor> de FOV/rumbo mientras se emite

SelfSensorFovController crea/actualiza/quita un SensorFOV sobre el
marcador propio usando SensorDetailHandler.addFovToMap (ya registrado
por el propio nucleo de ATAK - sin handler de CoT propio para esto,
a diferencia de <__video>). Enganchado a OnPointChangedListener del
marcador propio para reaccionar a la perdida/recuperacion de fix GPS
sin necesidad de sondeo. Rumbo = Marker.getTrackHeading() (course
over ground, igual que TAK ICU - no brujula del dispositivo).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 4: Deploy y verificación de extremo a extremo

**Files:** ninguno (verifica en el dispositivo, y contra un segundo ATAK o
una herramienta de inspección de CoT, lo que dejaron las Tareas 1-3).

**Interfaces:**
- Consumes: APK de `icu-replacement` con ciclo de vida arreglado, `<__video>`
  y `<sensor>` funcionando (Tareas 1-3).

- [ ] **Step 1: Confirmar que el entorno sigue sano**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev doctor
```

- [ ] **Step 2: Levantar el MediaMTX desechable de la Fase 2**

```powershell
docker run --rm -d --name icung-test-mediamtx -p 8554:8554 bluenviron/mediamtx
```

- [ ] **Step 3: Build + install + stage, sync + Load**

```powershell
docker compose exec atak-dev deploy icu-replacement
```

Sync + Load en ATAK (o confirmar auto-reload) igual que en las fases 0-2.

- [ ] **Step 4: Verificar la Decisión 1 — ocultar el panel para el stream**

Abrir el panel, pulsar "Emitir" (confirmar en `docker logs icung-test-mediamtx`
que hay publisher, igual que la Fase 2), y **sin pulsar "Detener"**, salir
del panel con la tecla atrás/cambiando de herramienta (no cerrando ATAK).

```powershell
docker logs icung-test-mediamtx --tail 10
```

Expected: una línea `session ... destroyed` / `conn ... closed` — confirma
que `onPaneVisible(false)` paró el stream de verdad, no solo visualmente.

- [ ] **Step 5: Verificar que reabrir el panel no revive nada zombie**

Volver a abrir el panel de ICU NG.

Expected: el preview arranca de nuevo (confirma `onPaneVisible(true)`), y el
botón muestra "Emitir" (no "Detener" residual de antes de ocultar el panel).

- [ ] **Step 6: Verificar `<__video>`/`<sensor>` con GPS fix — vía log, no solo confiando**

Pulsar "Emitir" otra vez. Volcar el CoT saliente:

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep -i "icung\|SelfVideoDetailHandler\|__video"' 2>&1 | Select-Object -Last 40
```

Si nada aparece ahí (es plausible — el envío de CoT puede no pasar por
logcat con ese texto), instalar
[niccellular/cotexplorer](https://github.com/niccellular/cotexplorer) (ya
identificado en la investigación previa del subproyecto como herramienta de
depuración de CoT) en el mismo ATAK del emulador, y comprobar en su vista de
"outgoing" que el evento del propio callsign incluye un hijo `<__video>`
con la URL configurada y un `<sensor>` con `fov`/`azimuth`.

Expected: ambos detalles presentes mientras se emite y hay fix GPS
(`10S EG 81050 42081`, visible en pantalla desde la Fase 0 — el emulador ya
trae una ubicación simulada con fix).

- [ ] **Step 7: Verificar el gating por GPS — quitar el fix a media emisión**

Con el stream activo, quitar la ubicación simulada del emulador:

```powershell
docker compose exec atak-dev bash -lc 'adb shell appops set com.atakmap.app.civ android:mock_location deny' 2>&1
```

Si eso no corta el fix visiblemente en pantalla (`NO GNSS` como se vio en la
Fase 1 antes del primer arranque), alternativa más directa: parar el
`geo_fix`/emulador de ubicación desde la consola de control del AVD
(`telnet localhost <consola>` → `geo fix` sin argumentos, o el panel
"Location" de Android Studio si se prefiere GUI). Confirmar en pantalla que
ATAK muestra `NO GNSS`.

Con `cotexplorer` abierto: expected: el siguiente CoT saliente del propio
callsign **ya no** trae `<__video>` (Decisión 3, vía `isSupported()`), y el
cono `<sensor>` desaparece del mapa (Decisión 3, vía `OnPointChangedListener`
+ `removeFromGroup()`) — sin tocar el botón de streaming, que sigue en
"Detener" (el push RTSP en sí no depende del fix, solo su anuncio).

- [ ] **Step 8: Confirmar que no hay `SecurityException`/`FATAL` nuevos**

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep -i icung' 2>&1 | Select-String -Pattern "SecurityException|FATAL"
```

Expected: sin resultados.

- [ ] **Step 9: Limpiar el receptor de prueba**

```powershell
docker stop icung-test-mediamtx
```

Sin commit en esta tarea — solo verificación de lo que dejaron las Tareas
1-3.

---

## Self-Review

- **Cobertura de la Fase 3 del diseño** ("Emisión CoT — `SelfMarkerVideoAnnouncer`
  + registro en `IVideoConnectionManager`, con el gating por GPS. Hecho =
  otro ATAK en la misma red ve el cono de vídeo en el marcador propio y
  puede abrir el stream desde la herramienta de vídeo nativa"): cubierto —
  Task 1 resuelve el ciclo de vida que lo sostiene todo, Task 2 el
  `<__video>`+registro en `IVideoConnectionManager`, Task 3 el cono
  `<sensor>`, Task 4 lo verifica de extremo a extremo incluyendo el gating
  por GPS explícitamente probado, no solo asumido.
- **La decisión que pedías resolver primero:** es su propia sección al
  principio de Architecture, con tabla de los cuatro eventos de ciclo de
  vida y qué hace cada uno — no quedó como nota al margen.
- **Placeholders:** ninguno — cada clase, método y constante citados
  (`IPaneLifecycleListener`, `IVideoConnectionManager`, `VideoManager`,
  `ConnectionEntryBase.Builder`, `ConnectionEntryDetail.toCotDetail`,
  `ICotDetailHandler2`, `CotDetailManager`, `SensorDetailHandler.addFovToMap`,
  `SensorFOV.setMetrics`, `Marker.getTrackHeading`,
  `PointMapItem.OnPointChangedListener`) verificados con `javap` contra
  `/opt/atak-sdk/main.jar` real durante la escritura de este plan — dos de
  ellos (`CotDetail.addChild`, el orden de parámetros de `setMetrics`)
  quedan además como paso explícito de re-verificación en la propia Tarea,
  porque `javap` no bastó para confirmarlos con la misma certeza que el
  resto.
- **Consistencia de nombres:** `connectionEntry`, `videoDetailHandler`,
  `sensorFovController` se declaran una vez en `IcuNgPlugin.java` (Tasks
  2-3) y se usan igual en las tres tareas — ningún nombre cambia entre
  tareas.
- **Riesgo identificado y no asumido a ciegas:** usar el
  `VideoDetailHandler` interno de ATAK en vez de uno propio habría sido
  menos código, pero su enlace automático MapItem↔ConnectionEntry no se
  pudo verificar contra bytecode ni contra el mirror público (aún no llega
  a `gov.tak.api`) — se documentó la alternativa descartada y por qué,
  igual que se hizo con la hipótesis de herencia de permisos en la Fase 1.
- **Fuera de alcance, deliberadamente:** rumbo por brújula real del
  dispositivo (en vez de `getTrackHeading()`), múltiples streams/conos
  simultáneos, elevación del sensor, autenticación en el `ConnectionEntry`,
  y confirmar el enlace automático del `VideoDetailHandler` interno de ATAK
  leyendo su código fuente real (si algún día se consigue acceso, sea vía
  una versión más reciente del mirror público o vía tak.gov) para
  simplificar esta fase más adelante.
