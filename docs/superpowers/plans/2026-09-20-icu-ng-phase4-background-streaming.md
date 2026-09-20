# ICU NG — Fase 4: streaming en segundo plano — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Si ICU NG está emitiendo cuando el usuario cierra u oculta su
panel para usar otra herramienta de ATAK, el push RTSP (y el `<__video>`/
`<sensor>` de la Fase 3, que dependen de él) **siguen vivos** — en vez de
pararse siempre, como hace la Fase 3 desde el fix del ciclo de vida. Ver
[2026-09-17-icu-ng-design.md](../specs/2026-09-17-icu-ng-design.md),
sección "Fuera de alcance de v1" ("streaming en segundo plano").

**Architecture:** Nada de esto toca `SelfVideoDetailHandler` ni
`SelfSensorFovController` — los dos ya dependen solo de
`camera.isStreaming()` + fix GPS (Fase 3), sin ninguna referencia a si el
panel está visible. Todo el trabajo de esta fase es en `IcuNgPlugin.java`:
decidir, al ocultar/cerrar el panel, si el `RtspCamera2` se para del todo
(como ahora) o se mantiene vivo enganchado a un render target sin vista.

### Decisión 1: alcance exacto de "segundo plano" — solo mientras ATAK sigue en primer plano

**Cubre:** el usuario navega a otra herramienta de ATAK sin cerrar ATAK
mismo — nuestro panel se oculta/cierra, pero el proceso `com.atakmap.app.civ`
sigue siendo la app en primer plano de Android todo el rato.

**No cubre:** mantener el stream si se bloquea la pantalla o se manda ATAK
entero a segundo plano (botón Home). Verificado contra el código fuente de
RootEncoder: no existe ningún mecanismo de `Foreground Service` ni de
gestión del ciclo de vida del proceso Android en `Camera2Base` ni en
`RtspCamera2` — lo único que ofrece la librería es cambiar el *render
target* del encoder (`OpenGlView` ↔ `GlStreamInterface`), nunca sobrevivir a
que Android congele/mate el proceso. Soportar el caso "ATAK en segundo
plano de verdad" necesitaría un `Foreground Service` propio con su
declaración de tipo de servicio y permiso de cámara en segundo plano — un
proyecto bastante más grande, deliberadamente fuera de esta fase.

### Decisión 2: mecanismo — `Camera2Base.replaceView()`, no parar/reiniciar el stream

Verificado contra el código fuente real (`Camera2Base.java`,
`RtspCamera2.kt`, rama `master` de
[pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder)):

- `RtspCamera2` tiene **dos constructores**, ambos ya conocidos por esta
  fase o por fases anteriores: `RtspCamera2(OpenGlView, ConnectChecker)`
  (el que ya usa la Fase 1) y `RtspCamera2(Context, ConnectChecker)` — este
  segundo, heredado de `Camera2Base(Context)`, construye internamente un
  `GlStreamInterface(context)` en vez de un `OpenGlView`, y pone
  `isBackground = true`.
- `Camera2Base` expone `replaceView(Context)` y `replaceView(OpenGlView)` —
  el primero cambia al render target sin vista, el segundo vuelve a una
  vista real. Los dos llaman a un método privado común,
  `replaceGlInterface(GlInterface)`, cuyo cuerpo completo se verificó:

  ```java
  private void replaceGlInterface(GlInterface glInterface) {
      if (isStreaming() || isRecording() || isOnPreview()) {
          Point size = this.glInterface.getEncoderSize();
          Point sizeRecord = this.glInterface.getEncoderSize();
          cameraManager.closeCamera();
          this.glInterface.removeMediaCodecSurface();
          this.glInterface.removeMediaCodecRecordSurface();
          this.glInterface.stop();
          this.glInterface = glInterface;
          // ... ajusta w/h según rotación ...
          prepareGlView(w, h, recordW, recordH, rotation);
          cameraManager.openLastCamera();
      } else {
          this.glInterface = glInterface;
      }
  }
  ```

  **No llama a `stopStream()` ni a `stopStreamImp()` en ningún punto** —
  solo cierra y reabre la cámara local y reengancha el render target. La
  sesión RTSP hacia el servidor (`streamClient`, lo que gestiona
  `startStream()`/`stopStream()`) es un objeto totalmente distinto dentro de
  `Camera2Base` y no se toca. Esto confirma que `replaceView()` es
  justo lo que hace falta: cambia de dónde sale el vídeo localmente sin
  interrumpir el push.
- `prepareGlView(...)` (verificado también) llama a
  `glInterface.setEncoderSize(w, h)` y, si aplica,
  `glInterface.setEncoderRecordSize(recordW, recordH)` **antes** de
  `glInterface.start()` — así que `GlStreamInterface` (que, verificado en su
  propio código, exige que se llame a esos setters antes de `start()`)
  queda correctamente inicializado sin que este plugin tenga que llamar a
  nada de eso a mano. Todo el trabajo de re-enganche está ya resuelto
  dentro de RootEncoder — el plugin solo llama a `replaceView(...)`.

### Decisión 3: solo se mantiene vivo si realmente se está emitiendo

Si el usuario cierra el panel con la cámara solo en preview (sin haber
pulsado "Emitir"), se sigue parando todo exactamente como en la Fase 3 — no
hay motivo para mantener la cámara encendida en segundo plano si no está
publicando nada. `tearDownPane()` decide esto mirando
`camera.isStreaming()`, el mismo booleano que ya usa el resto del plugin.

**Tech Stack:** Mismas clases de RootEncoder ya integradas
(`RtspCamera2`/`Camera2Base`) — un método nuevo, `replaceView`, verificado
contra el código fuente real, no de memoria. Nada nuevo del lado de ATAK.

## Global Constraints

- Todo lo de `icu_ng_atak` (emulador) y `tools/atak-plugin-dev` (contenedor)
  de las fases anteriores aplica igual — `doctor` primero siempre.
- **El mismo gotcha de la Fase 1 vuelve a aplicar:** `replaceView(previewView)`
  toca el `Surface` de la `OpenGlView`, así que tiene que esperar a
  `surfaceCreated()` igual que `startPreview()` — nunca llamarlo justo tras
  inflar el layout. La `View`/`OpenGlView` en sí **siempre** se reconstruye
  desde cero en cada apertura del panel (eso no cambia respecto a la Fase
  3) — solo el objeto `RtspCamera2` es lo que a veces se reutiliza. Por eso
  el patrón fiable ya probado varias veces (`SurfaceHolder` nuevo →
  `surfaceCreated()` → tocar la cámara) sigue aplicando sin cambios.
- `onStop()` (el plugin entero se detiene, no solo el panel) sigue parando
  todo sin excepción — `tearDownPane()` con su rama "mantener en segundo
  plano" es solo para ocultar/cerrar el panel, nunca para el apagado del
  plugin.

## File Structure

```
icu-replacement/
└── app/src/main/java/com/carlosgt/atak/icung/plugin/
    └── IcuNgPlugin.java   (modificar: tearDownPane() y showPane() con la rama de segundo plano)
```

Ningún fichero nuevo — `SelfVideoDetailHandler.java` y
`SelfSensorFovController.java` de la Fase 3 no se tocan (ver Architecture).

## Task 1: `tearDownPane()` decide entre parar del todo o pasar a segundo plano

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Produces: campo `activeStreamUrl` (String) — la Tarea 2 lo lee para
  rellenar el campo de URL al reconstruir el panel sobre una cámara que
  sigue emitiendo.

- [ ] **Step 1: Añadir el campo `activeStreamUrl`**

Junto a `connectionEntry`:

```java
    ConnectionEntry connectionEntry;
    String activeStreamUrl;
```

- [ ] **Step 2: Guardar la URL activa al arrancar el stream**

En `toggleStream()`, en la rama de arranque (la que ya hace
`camera.startStream(url)`), añadir la asignación justo antes:

```java
        } else {
            String url = rtspUrlInput.getText().toString();
            activeStreamUrl = url;
            camera.startStream(url);
```

- [ ] **Step 3: Reescribir `tearDownPane()` con la rama de segundo plano**

Reemplazar el método completo:

```java
    // Called whenever the panel stops being visible for any reason (hidden
    // or closed). If ICU NG is actively streaming, keeps it alive headless
    // (Fase 4) instead of stopping it - replaceView(Context) only swaps the
    // local camera->encoder render target (verified: it never touches
    // startStream()/stopStream()), so the RTSP push, and the <__video>/
    // <sensor> from Fase 3 that key off isStreaming(), stay up. If it was
    // only previewing (never pressed Emitir), there's nothing worth keeping
    // alive - same full stop as before.
    private void tearDownPane() {
        templatePane = null;

        if (camera == null) {
            return;
        }

        if (camera.isStreaming()) {
            camera.replaceView(pluginContext);
        } else {
            stopCameraAndStream();
            camera = null;
        }
    }
```

- [ ] **Step 4: Compilar**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
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
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): mantener el stream vivo en segundo plano al cerrar el panel

tearDownPane() ahora distingue: si camera.isStreaming(), pasa a un
render target sin vista (camera.replaceView(pluginContext)) en vez de
pararlo todo - verificado contra el codigo de Camera2Base que
replaceGlInterface() nunca toca startStream()/stopStream(), solo
reengancha la camara local. Si solo estaba en preview, sigue parando
todo como en la Fase 3 - no hay nada que mantener vivo.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 2: `showPane()` reengancha una cámara viva en vez de crear una nueva

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `activeStreamUrl` (Tarea 1) — se usa para rellenar
  `rtspUrlInput` al reabrir sobre una cámara que ya estaba emitiendo.

- [ ] **Step 1: Distinguir construcción nueva de reenganche**

Dentro de `showPane()`, reemplazar el bloque `if(templatePane == null) { ... }`
completo por:

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

            // Fase 4: si camera ya existe, es una que la Fase 4 dejo viva en
            // segundo plano (tearDownPane) - se reengancha en vez de crear
            // una nueva, y la UI refleja que sigue emitiendo.
            final boolean resumingBackgroundCamera = camera != null;
            if (resumingBackgroundCamera) {
                rtspUrlInput.setText(activeStreamUrl);
                streamToggleButton.setText(R.string.stop_stream);
            } else {
                camera = new RtspCamera2(previewView, this);
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

- [ ] **Step 2: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Si falla con `variable resumingBackgroundCamera
might not have been initialized` o similar, comprobar que quedó declarada
`final` y asignada antes del `addCallback(...)`.

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
feat(icu-replacement): reenganchar el preview a una camara que sigue emitiendo

showPane() distingue construir una RtspCamera2 nueva (de siempre) de
reenganchar una que la Fase 4 dejo viva en segundo plano
(camera.replaceView(previewView), disparado desde el mismo
surfaceCreated() que ya se usaba) - la UI (URL, texto del boton)
tambien se restaura para reflejar que sigue emitiendo. paneView/
previewView se siguen reconstruyendo desde cero siempre; solo camera
es lo que a veces se reutiliza.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 3: Deploy y verificación — el push no se corta al cerrar el panel

**Files:** ninguno (verifica en el dispositivo, y contra un receptor
externo, lo que dejaron las Tareas 1-2).

**Interfaces:**
- Consumes: APK de `icu-replacement` con `tearDownPane()`/`showPane()`
  reconstruidos (Tareas 1-2), y un MediaMTX desechable como receptor de
  prueba (igual que en las fases 2-3).

- [ ] **Step 1: Confirmar que el entorno sigue sano**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev doctor
```

- [ ] **Step 2: Levantar el MediaMTX desechable**

```powershell
docker run --rm -d --name icung-test-mediamtx -p 8554:8554 bluenviron/mediamtx
```

- [ ] **Step 3: Build + install + stage, sync + Load**

```powershell
docker compose exec atak-dev deploy icu-replacement
```

Sync + Load en ATAK igual que en las fases anteriores.

- [ ] **Step 4: Emitir, cerrar el panel, y confirmar que MediaMTX NO corta la sesión**

Abrir el panel, pulsar "Emitir" (confirmar publisher en
`docker logs icung-test-mediamtx --tail 10`, igual que fases anteriores).
Cerrar el panel con la tecla atrás (sin pulsar "Detener").

```powershell
docker logs icung-test-mediamtx --tail 10
```

Expected: **ninguna** línea nueva de `destroyed`/`closed` tras cerrar el
panel — la sesión RTSP sigue publicando exactamente igual que antes de
cerrarlo. Esto es lo contrario de lo que se verificó en la Fase 3 (ahí sí
se esperaba el corte) — la diferencia es el punto de esta fase.

- [ ] **Step 5: Confirmar que el cono `<sensor>` sigue vivo con el panel cerrado**

Sin abrir el panel, centrar y hacer zoom sobre el marcador propio (icono de
"bloquear en" + botón `+` de zoom, como ya se hizo en la Fase 3).

Expected: el cono sigue visible en el mapa — confirma en el dispositivo lo
que la Architecture predijo por código: `SelfSensorFovController` no mira
si el panel está abierto, solo `camera.isStreaming()` (que sigue siendo
`true`).

- [ ] **Step 6: Reabrir el panel y confirmar el reenganche, sin re-publicar**

Volver a abrir el panel de ICU NG.

Expected: el preview en vivo se ve de nuevo (la cámara real, no un
placeholder), el campo de URL muestra la URL con la que se emitía, y el
botón dice **"Detener"** (no "Emitir" — confirma que el estado de UI se
restauró correctamente). En `docker logs icung-test-mediamtx`: sin ninguna
línea nueva de `is publishing` — sigue siendo la **misma** sesión de antes,
no una nueva reconexión.

- [ ] **Step 7: Pulsar "Detener" y confirmar el corte limpio de siempre**

Expected: el botón vuelve a "Emitir", MediaMTX registra `destroyed`/
`closed` en su log, y el cono `<sensor>` desaparece — comportamiento
idéntico al ya verificado en fases anteriores, sin regresión.

- [ ] **Step 8: Verificación por log — sin crash**

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep -i icung' 2>&1 | Select-String -Pattern "FATAL|SecurityException"
```

Expected: sin resultados.

- [ ] **Step 9: Limpiar el receptor de prueba**

```powershell
docker stop icung-test-mediamtx
```

Sin commit en esta tarea — solo verificación de lo que dejaron las Tareas
1-2.

---

## Self-Review

- **Cobertura del alcance elegido** ("streaming en segundo plano" de
  "Fuera de alcance de v1"): cubierto — Task 1 decide cuándo mantener vivo,
  Task 2 reengancha la UI sobre la cámara viva, Task 3 verifica en el
  dispositivo que el push realmente no se corta y que la Fase 3
  (`<sensor>`/`<__video>`) sigue funcionando sin tocarla.
- **Placeholders:** ninguno — `replaceView`, `replaceGlInterface`,
  `prepareGlView`, `GlStreamInterface`, los dos constructores de
  `RtspCamera2`, todos citados contra el código fuente real de RootEncoder,
  no de memoria (la reescritura mental que tenía de esta librería antes de
  la Fase 1 ya resultó estar desactualizada una vez).
- **La decisión de alcance que pedías resolver primero** (qué es y qué no
  es "segundo plano" aquí): es su propia sección al principio de
  Architecture, con la evidencia de por qué el límite se traza donde se
  traza (RootEncoder no ofrece nada de gestión de proceso Android).
- **Consistencia de nombres:** `activeStreamUrl` se declara en la Tarea 1 y
  se usa igual en la Tarea 2. `resumingBackgroundCamera` es local a
  `showPane()`, no se expone fuera.
- **Riesgo identificado y no asumido a ciegas:** en vez de asumir que
  `replaceView()` no interrumpe el stream porque "suena razonable", se leyó
  el cuerpo completo de `replaceGlInterface()` para confirmar que
  literalmente no invoca ningún método de parada/arranque de stream — la
  misma disciplina que evitó otro crash a ciegas en fases anteriores.
- **Fuera de alcance, deliberadamente:** streaming mientras ATAK entero
  está en segundo plano o con la pantalla bloqueada (necesitaría un
  Foreground Service — ver Decisión 1), persistencia de la URL escrita a
  mano entre reconstrucciones cuando *no* se estaba emitiendo (sigue
  reseteándose al valor por defecto del layout, sin cambios respecto a las
  fases anteriores — no es parte de lo que se pidió aquí).
